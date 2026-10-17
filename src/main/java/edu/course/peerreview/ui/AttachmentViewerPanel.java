package edu.course.peerreview.ui;

import edu.course.peerreview.domain.Domain.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;

public final class AttachmentViewerPanel extends JPanel {
    private final JTable table=Ui.table("文件名","大小","文本状态");
    public AttachmentViewerPanel(String title,List<AttachmentInfo> files,LongFunction<AttachmentDownload> downloader){
        super(new BorderLayout(0,4));setBorder(BorderFactory.createTitledBorder(title));table.setRowHeight(30);
        Ui.rows(table,files.stream().map(f -> new Object[]{f.name(),String.format(Locale.ROOT,"%.1f KiB",f.size()/1024.0),AttachmentUi.status(f.textStatus())}).toList());
        JButton preview=Ui.button("预览附件正文",()->load(files,downloader,true));JButton save=Ui.button("另存附件",()->load(files,downloader,false));preview.setEnabled(!files.isEmpty());save.setEnabled(!files.isEmpty());
        add(new JScrollPane(table));add(Ui.toolbar(preview,save),BorderLayout.SOUTH);setPreferredSize(new Dimension(500,145));
    }
    private void load(List<AttachmentInfo> files,LongFunction<AttachmentDownload> downloader,boolean preview){
        long id=files.get(Ui.selected(table)).id();Window owner=SwingUtilities.getWindowAncestor(this);
        Ui.work(owner,()->downloader.apply(id),data->{if(preview)AttachmentUi.preview(owner,data);else AttachmentUi.save(owner,data);});
    }
    public static void show(Window owner,String title,String body,List<AttachmentInfo> files,LongFunction<AttachmentDownload> downloader){
        JDialog dialog=new JDialog(owner,title,Dialog.ModalityType.APPLICATION_MODAL);JPanel main=new JPanel(new BorderLayout(8,8));main.setBorder(BorderFactory.createEmptyBorder(12,14,12,14));
        main.add(new JScrollPane(Ui.area(body.isBlank()?"本次为文档提交，请预览附件正文或另存文档人工查看。":body,false)));main.add(new AttachmentViewerPanel("文档附件",files,downloader),BorderLayout.SOUTH);
        dialog.add(main);dialog.add(Ui.toolbar(Ui.button("关闭",dialog::dispose)),BorderLayout.SOUTH);dialog.setSize(900,700);dialog.setLocationRelativeTo(owner);dialog.setVisible(true);dialog.dispose();
    }
}
