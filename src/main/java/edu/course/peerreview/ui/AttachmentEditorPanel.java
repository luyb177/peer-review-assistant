package edu.course.peerreview.ui;

import edu.course.peerreview.attachment.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.BusinessException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.function.LongFunction;

/** Stages changes locally; parent save commits content and attachment edits as one transaction. */
public final class AttachmentEditorPanel extends JPanel {
    private record Row(AttachmentInfo saved,PreparedAttachment staged){
        String name(){return staged==null?saved.name():staged.name();}long size(){return staged==null?saved.size():staged.size();}
        String hash(){return staged==null?saved.sha256():staged.sha256();}String status(){return staged==null?saved.textStatus():staged.textStatus();}
    }
    private final List<Row> rows=new ArrayList<>();
    private final Set<Long> original=new HashSet<>(),removed=new HashSet<>();
    private final JTable table=Ui.table("文件名","大小","文本状态");
    private final LongFunction<AttachmentDownload> downloader;
    public AttachmentEditorPanel(List<AttachmentInfo> existing,LongFunction<AttachmentDownload> downloader){
        super(new BorderLayout(0,4));this.downloader=downloader;existing.forEach(f->{original.add(f.id());rows.add(new Row(f,null));});setBorder(BorderFactory.createTitledBorder("文档附件：PDF / DOC / DOCX / TXT / MD，每个 ≤5 MiB，最多 3 个"));table.setRowHeight(30);
        add(new JScrollPane(table));add(Ui.toolbar(Ui.button("添加附件",()->choose(false)),Ui.button("替换附件",()->choose(true)),Ui.button("移除附件",this::remove),Ui.button("预览正文",this::preview)),BorderLayout.SOUTH);setPreferredSize(new Dimension(700,200));refresh();
    }
    public void stage(PreparedAttachment file){validate(List.of(file),-1);rows.add(new Row(null,file));refresh();}
    public AttachmentChanges changes(){return new AttachmentChanges(original,removed,rows.stream().filter(r->r.staged()!=null).map(Row::staged).toList());}
    private void refresh(){Ui.rows(table,rows.stream().map(r->new Object[]{r.name(),String.format(Locale.ROOT,"%.1f KiB",r.size()/1024.0),AttachmentUi.status(r.status())}).toList());}
    private void remove(){Row r=rows.remove(Ui.selected(table));if(r.saved()!=null)removed.add(r.saved().id());refresh();}
    private void validate(List<PreparedAttachment> additions,int replace){
        if(rows.size()+additions.size()-(replace<0?0:1)>PreparedAttachment.MAX_FILES)throw new BusinessException("最多添加 3 个附件。");
        Set<String> hashes=new HashSet<>();for(int i=0;i<rows.size();i++)if(i!=replace)hashes.add(rows.get(i).hash());for(var f:additions)if(!hashes.add(f.sha256()))throw new BusinessException("不能重复添加相同文件内容。");
    }
    private void choose(boolean replace){
        int selected=replace?Ui.selected(table):-1;JFileChooser chooser=new JFileChooser();chooser.setMultiSelectionEnabled(!replace);chooser.setFileFilter(new FileNameExtensionFilter("文档（PDF、Word、文本）","pdf","doc","docx","txt","md"));Window owner=SwingUtilities.getWindowAncestor(this);
        if(chooser.showOpenDialog(owner)!=JFileChooser.APPROVE_OPTION)return;
        java.io.File[] chosen=replace?new java.io.File[]{chooser.getSelectedFile()}:chooser.getSelectedFiles();if(chosen.length==0)chosen=new java.io.File[]{chooser.getSelectedFile()};
        List<Path> paths=Arrays.stream(chosen).map(java.io.File::toPath).toList();
        Ui.work(owner,()->paths.stream().map(PreparedAttachment::read).toList(),prepared->{validate(prepared,selected);if(selected>=0){Row old=rows.remove(selected);if(old.saved()!=null)removed.add(old.saved().id());}prepared.forEach(f->rows.add(new Row(null,f)));refresh();});
    }
    private void preview(){
        Row r=rows.get(Ui.selected(table));Window owner=SwingUtilities.getWindowAncestor(this);
        if(r.staged()!=null){var f=r.staged();AttachmentUi.preview(owner,new AttachmentDownload(new AttachmentInfo(0,f.name(),f.mediaType(),f.size(),f.sha256(),f.textStatus(),LocalDateTime.now()),f.content(),f.text()));}
        else Ui.work(owner,()->downloader.apply(r.saved().id()),data->AttachmentUi.preview(owner,data));
    }
}
