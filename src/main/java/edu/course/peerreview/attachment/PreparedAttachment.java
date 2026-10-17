package edu.course.peerreview.attachment;

import edu.course.peerreview.service.BusinessException;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Fully validated immutable upload; only this factory can create its hash and extracted text. */
public final class PreparedAttachment {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_FILES = 3;
    public static final int MAX_TEXT = 20000;
    private static final Map<String,String> TYPES = Map.of("pdf","application/pdf","doc","application/msword","docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document","txt","text/plain","md","text/markdown");
    private final String name, mediaType, hash, text, status;
    private final byte[] bytes;
    private PreparedAttachment(String name, byte[] bytes, String type, String hash, DocumentText.Result result) {
        this.name=name;this.bytes=bytes;this.mediaType=type;this.hash=hash;this.text=result.text();this.status=result.status();
    }
    public static PreparedAttachment read(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new BusinessException("请选择普通文档文件，不支持目录或符号链接。");
        try (InputStream in=Files.newInputStream(path)) { return of(path.getFileName().toString(),in.readNBytes(MAX_BYTES+1)); }
        catch(IOException e){throw new BusinessException("文档读取失败，请检查文件是否存在及读取权限。");}
    }
    public static PreparedAttachment of(String name, byte[] content) {
        if(name==null || name.isBlank() || name.length()>255 || java.util.regex.Pattern.compile("[\\p{Cntrl}\\\\/:*?\"<>|]").matcher(name).find() || name.endsWith(".") || name.endsWith(" ")) throw new BusinessException("附件文件名不合法，不能包含路径、控制字符或 Windows 保留字符。");
        String ext=extension(name);if(!TYPES.containsKey(ext))throw new BusinessException("只支持 PDF、DOC、DOCX、TXT 和 MD 文档。");
        String base=name.substring(0,name.lastIndexOf('.')).toUpperCase(Locale.ROOT);if(base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")||base.isBlank())throw new BusinessException("附件文件名使用了系统保留名称。");
        if(content==null||content.length==0)throw new BusinessException("不能上传空文件。");
        if(content.length>MAX_BYTES)throw new BusinessException("每个附件不能超过 5 MiB。");
        byte[] bytes=content.clone();
        DocumentText.Result result=DocumentText.extract(ext,bytes);
        try{return new PreparedAttachment(name,bytes,TYPES.get(ext),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),result);}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static String extension(String name){int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}
    public String name(){return name;} public String mediaType(){return mediaType;} public String sha256(){return hash;}
    public long size(){return bytes.length;} public byte[] content(){return bytes.clone();} public String text(){return text;} public String textStatus(){return status;}
}
