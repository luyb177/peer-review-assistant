package edu.course.peerreview.ui;

import edu.course.peerreview.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AttachmentUiTest {
    @TempDir Path directory;
    @Test void actualPublishSubmitReplaceAndDownloadButtonsPersistDocuments()throws Exception{
        assumeFalse(GraphicsEnvironment.isHeadless());TestFixture f=new TestFixture();AttachmentService files=new AttachmentService(f.db);Assignment a=f.service.assignment(f.teacher,f.submissionId);User student=f.students.get(0);AtomicInteger callbacks=new AtomicInteger();List<Window> windows=new ArrayList<>();
        Path teacherFile=directory.resolve("教师要求.docx");Files.write(teacherFile,AttachmentDocumentsTest.docx("请讨论模块化、测试和异常处理"));Path first=directory.resolve("学生姓名-初稿.txt");Files.writeString(first,"第一次文档提交",StandardCharsets.UTF_8);Path replacement=directory.resolve("学生姓名-修订.txt");Files.writeString(replacement,"修改后的文档正文",StandardCharsets.UTF_8);
        try{
            AssignmentDialog publish=ui(()->{Ui.theme();return new AssignmentDialog(null,f.service,files,f.teacher,f.service.courses(f.teacher),a,List.of(),callbacks::incrementAndGet);});windows.add(publish);show(publish);
            chooseFile(publish,"添加附件",teacherFile);click(publish,"保存作业");await(()->!unchecked(publish::isShowing));assertEquals(1,files.assignmentFiles(student,a.id()).size());
            Assignment current=f.service.assignment(student,a.id());
            SubmissionDialog submit=ui(()->new SubmissionDialog(null,f.service,files,student,current,"",List.of(),callbacks::incrementAndGet));windows.add(submit);show(submit);chooseFile(submit,"添加附件",first);click(submit,"保存提交");await(()->!unchecked(submit::isShowing));
            assertEquals("",f.service.submissions(student,a.id()).get(0).content());AttachmentInfo old=files.ownSubmissionFiles(student,a.id()).get(0);
            SubmissionDialog edit=ui(()->new SubmissionDialog(null,f.service,files,student,current,"",files.ownSubmissionFiles(student,current.id()),callbacks::incrementAndGet));windows.add(edit);show(edit);
            ui(()->{find(edit,JTable.class).get(0).setRowSelectionInterval(0,0);return null;});chooseFile(edit,"替换附件",replacement);click(edit,"保存提交");await(()->!unchecked(edit::isShowing));AttachmentInfo saved=files.ownSubmissionFiles(student,a.id()).get(0);assertNotEquals(old.id(),saved.id());
            JDialog view=ui(()->{JDialog d=new JDialog((Window)null,"附件下载测试");d.add(new AttachmentViewerPanel("我的附件",List.of(saved),id->files.download(student,id)));d.setSize(800,350);return d;});windows.add(view);show(view);ui(()->{find(view,JTable.class).get(0).setRowSelectionInterval(0,0);return null;});
            click(view,"预览附件正文");JDialog preview=dialog("附件正文预览");assertTrue(ui(()->find(preview,JTextArea.class).get(0).getText()).contains("修改后的文档正文"));ui(()->{preview.dispose();return null;});
            click(view,"另存附件");await(()->activeChooser()!=null);assertEquals(saved.name(),ui(()->activeChooser().getSelectedFile().getName()));Path output=directory.resolve("download.txt");
            ui(()->{JFileChooser chooser=activeChooser();chooser.setSelectedFile(output.toFile());chooser.approveSelection();return null;});JDialog success=dialog("课堂同伴互评");accept(success);await(()->Files.exists(output));assertArrayEquals(Files.readAllBytes(replacement),Files.readAllBytes(output));assertEquals(3,callbacks.get());
        }finally{ui(()->{for(Window w:windows){for(Window child:w.getOwnedWindows())child.dispose();w.dispose();}return null;});}
    }
    @Test void blindReviewDownloadChooserDoesNotExposeOriginalFileName()throws Exception{
        assumeFalse(GraphicsEnvironment.isHeadless());TestFixture f=new TestFixture();AttachmentService files=new AttachmentService(f.db);User author=f.students.get(0);Assignment a=f.service.assignment(author,f.submissionId);Path document=directory.resolve("张同学-学号.txt");Files.writeString(document,"不包含身份的合成正文",StandardCharsets.UTF_8);
        f.service.submitWithAttachments(author,a.id(),"",edu.course.peerreview.attachment.AttachmentChanges.add(edu.course.peerreview.attachment.PreparedAttachment.read(document)));f.service.submit(f.students.get(1),a.id(),"其他正文");PeerReviewService review=f.at(a.reviewOpen().plusSeconds(1));review.allocate(f.teacher,a.id(),1);long sid=f.service.submissions(author,a.id()).get(0).id();ReviewTask task=review.tasks(f.teacher,a.id()).stream().filter(t->t.submissionId()==sid).findFirst().orElseThrow();User actor=f.students.stream().filter(u->u.id()==task.reviewerId()).findFirst().orElseThrow();
        List<AttachmentInfo> info=files.reviewFiles(actor,task.id());JDialog view=ui(()->{Ui.theme();JDialog d=new JDialog((Window)null,"盲评附件测试");d.add(new AttachmentViewerPanel("匿名附件",info,id->files.download(actor,id)));d.setSize(800,350);return d;});
        try{show(view);ui(()->{find(view,JTable.class).get(0).setRowSelectionInterval(0,0);return null;});click(view,"另存附件");await(()->activeChooser()!=null);assertEquals("匿名附件-1.txt",ui(()->activeChooser().getSelectedFile().getName()));ui(()->{activeChooser().cancelSelection();return null;});}
        finally{ui(()->{for(Window child:view.getOwnedWindows())child.dispose();view.dispose();return null;});}
    }
    private void chooseFile(Window owner,String button,Path path)throws Exception{click(owner,button);await(()->activeChooser()!=null);ui(()->{JFileChooser chooser=activeChooser();chooser.setSelectedFile(path.toFile());if(chooser.isMultiSelectionEnabled())chooser.setSelectedFiles(new java.io.File[]{path.toFile()});chooser.approveSelection();return null;});await(()->unchecked(()->owner.isEnabled()&&find(owner,JTable.class).stream().anyMatch(t->{for(int r=0;r<t.getRowCount();r++)if(path.getFileName().toString().equals(t.getValueAt(r,0)))return true;return false;})));}
    private JFileChooser activeChooser(){return unchecked(()->Arrays.stream(Window.getWindows()).filter(Window::isShowing).flatMap(w->find(w,JFileChooser.class).stream()).findFirst().orElse(null));}
    private void show(JDialog d)throws Exception{SwingUtilities.invokeLater(()->d.setVisible(true));await(()->unchecked(d::isShowing));}
    private void click(Component root,String text)throws Exception{JButton b=ui(()->find(root,JButton.class).stream().filter(x->text.equals(x.getText())&&x.isShowing()).findFirst().orElseThrow());assertTrue(ui(b::isEnabled));SwingUtilities.invokeLater(b::doClick);}
    private JDialog dialog(String title)throws Exception{await(()->unchecked(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof JDialog d&&w.isShowing()&&d.getTitle().contains(title))));return ui(()->Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&w.isShowing()&&d.getTitle().contains(title)).map(JDialog.class::cast).reduce((a,b)->b).orElseThrow());}
    private void accept(JDialog popup)throws Exception{JButton b=ui(()->popup.getRootPane().getDefaultButton());SwingUtilities.invokeLater(b::doClick);await(()->!unchecked(popup::isShowing));}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long until=System.nanoTime()+java.time.Duration.ofSeconds(12).toNanos();while(System.nanoTime()<until){if(condition.getAsBoolean())return;Thread.sleep(40);}fail("Timed out waiting for attachment UI event");}
    private <T>T ui(Callable<T> action)throws Exception{if(SwingUtilities.isEventDispatchThread())return action.call();FutureTask<T> task=new FutureTask<>(action);SwingUtilities.invokeAndWait(task);return task.get();}
    private <T>T unchecked(Callable<T> action){try{return ui(action);}catch(Exception e){throw new IllegalStateException(e);}}
    private <T extends Component>List<T>find(Component root,Class<T> type){List<T> result=new ArrayList<>();if(type.isInstance(root))result.add(type.cast(root));if(root instanceof Container c)for(Component child:c.getComponents())result.addAll(find(child,type));return result;}
}
