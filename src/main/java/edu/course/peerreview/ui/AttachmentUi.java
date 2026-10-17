package edu.course.peerreview.ui;

import edu.course.peerreview.attachment.AttachmentFiles;
import edu.course.peerreview.domain.Domain.AttachmentDownload;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;

final class AttachmentUi {
    private AttachmentUi() { }
    static String status(String value){return switch(value){case "EXTRACTED" -> "可读取正文";case "NO_TEXT" -> "无可提取文字（可能为扫描件）";case "TEXT_TOO_LONG" -> "正文过长，请提供摘要";case "ENCRYPTED" -> "加密文档，请提供摘要";default -> "无法提取正文";};}
    static void preview(Window owner,AttachmentDownload data){
        if(data.text().isBlank()){Ui.info(owner,status(data.info().textStatus())+"。\n原文档仍可另存后人工查看；系统不会假称 AI 已阅读。");return;}
        Ui.textDialog(owner,"附件正文预览 · "+data.info().name(),data.text());
    }
    static void save(Window owner,AttachmentDownload data){
        JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File(data.info().name()));
        if(chooser.showSaveDialog(owner)!=JFileChooser.APPROVE_OPTION)return;
        Path path=chooser.getSelectedFile().toPath();if(Files.exists(path)&&!Ui.confirm(owner,"目标文件已存在，确认覆盖？"))return;
        Ui.work(owner,()->{AttachmentFiles.save(path,data);return true;},v->Ui.info(owner,"附件已另存；系统不会自动打开或执行文档。"));
    }
}
