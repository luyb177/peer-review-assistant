package edu.course.peerreview.ui;

import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class SubmissionDialog extends JDialog {
    public SubmissionDialog(Window owner,PeerReviewService service,AttachmentService attachments,User actor,Assignment assignment,String old,List<AttachmentInfo> existing,Runnable updated){
        super(owner,"提交作业 · "+assignment.title(),Dialog.ModalityType.APPLICATION_MODAL);JTextArea content=Ui.area(old,true);
        AttachmentEditorPanel files=new AttachmentEditorPanel(existing,id->attachments.download(actor,id));
        JPanel body=new JPanel(new BorderLayout(8,8));body.setBorder(BorderFactory.createEmptyBorder(14,16,14,16));
        body.add(new JLabel("可提交文本、文本加附件或仅文档；不可两者都空。请清理文档正文和内部属性中的姓名等信息。"),BorderLayout.NORTH);body.add(new JScrollPane(content));body.add(files,BorderLayout.SOUTH);add(body);
        add(Ui.toolbar(Ui.primary("保存提交",()->{String text=content.getText();var changes=files.changes();Ui.work(this,()->{service.submitWithAttachments(actor,assignment.id(),text,changes);return true;},v->{dispose();updated.run();});}),Ui.button("取消",this::dispose)),BorderLayout.SOUTH);
        setSize(960,780);setMinimumSize(new Dimension(850,680));setLocationRelativeTo(owner);
    }
}
