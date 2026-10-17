package edu.course.peerreview.attachment;

import edu.course.peerreview.domain.Domain.AttachmentDownload;
import edu.course.peerreview.service.BusinessException;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

public final class AttachmentFiles {
    private AttachmentFiles() { }
    /** Download is atomic with respect to an existing destination, including write failures. */
    public static void save(Path target,AttachmentDownload download){
        byte[] data=download.content();
        try{
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            if(data.length!=download.info().size()||!hash.equals(download.info().sha256()))throw new BusinessException("附件校验失败，未写入目标文件。");
            Path absolute=target.toAbsolutePath();Path temporary=Files.createTempFile(absolute.getParent(),".peer-review-download-",".tmp");
            try{Files.write(temporary,data);try{Files.move(temporary,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,absolute,StandardCopyOption.REPLACE_EXISTING);}}
            finally{Files.deleteIfExists(temporary);}
        }catch(IOException e){throw new BusinessException("附件另存失败，请检查目录权限、剩余空间或文件占用。");}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
