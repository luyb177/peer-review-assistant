package edu.course.peerreview;

import edu.course.peerreview.attachment.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.BusinessException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

public class AttachmentDocumentsTest {
    @TempDir Path directory;
    public static byte[] docx(String text)throws IOException{return docxXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>"+text.replace("&","&amp;").replace("<","&lt;")+"</w:t></w:r></w:p></w:body></w:document>");}
    static byte[] docxXml(String xml)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream out=new ZipOutputStream(bytes)){out.putNextEntry(new ZipEntry("[Content_Types].xml"));out.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>".getBytes(StandardCharsets.UTF_8));out.closeEntry();out.putNextEntry(new ZipEntry("_rels/.rels"));out.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>".getBytes(StandardCharsets.UTF_8));out.closeEntry();out.putNextEntry(new ZipEntry("word/document.xml"));out.write(xml.getBytes(StandardCharsets.UTF_8));out.closeEntry();}return bytes.toByteArray();}
    @Test void utf8BomGbkMarkdownAndDocxProduceReadableBoundedText()throws Exception{
        assertEquals("中文正文",PreparedAttachment.of("a.txt","\ufeff中文正文".getBytes(StandardCharsets.UTF_8)).text());
        assertEquals("中文正文",PreparedAttachment.of("b.txt","中文正文".getBytes(Charset.forName("GB18030"))).text());
        assertEquals("中文正文",PreparedAttachment.of("unicode.txt","中文正文".getBytes(StandardCharsets.UTF_16)).text());
        assertTrue(PreparedAttachment.of("c.md","# 模块化".getBytes(StandardCharsets.UTF_8)).text().contains("模块化"));
        assertEquals("课堂互评文档",PreparedAttachment.of("d.DOCX",docx("课堂互评文档")).text());
    }
    @Test void legacyWordDocFixtureIsReadable()throws Exception{
        try(InputStream input=getClass().getResourceAsStream("/attachments/poi-simple-doc/simple.doc")){assertNotNull(input);var file=PreparedAttachment.of("legacy.doc",input.readAllBytes());assertEquals("EXTRACTED",file.textStatus());assertFalse(file.text().isBlank());}
    }
    @Test void pdfWithTextAndScannedLikePdfAreDistinguished()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(PDDocument pdf=new PDDocument()){PDPage page=new PDPage();pdf.addPage(page);try(PDPageContentStream stream=new PDPageContentStream(pdf,page)){stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(40,700);stream.showText("Module design and boundary tests");stream.endText();}pdf.save(bytes);}
        var prepared=PreparedAttachment.of("work.pdf",bytes.toByteArray());assertEquals("EXTRACTED",prepared.textStatus());assertTrue(prepared.text().contains("boundary tests"));
        bytes.reset();try(PDDocument pdf=new PDDocument()){pdf.addPage(new PDPage());pdf.save(bytes);}assertEquals("NO_TEXT",PreparedAttachment.of("scan.pdf",bytes.toByteArray()).textStatus());
    }
    @Test void encryptedPdfRemainsDownloadableButNotPretendedReadable()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(PDDocument pdf=new PDDocument()){pdf.addPage(new PDPage());StandardProtectionPolicy protection=new StandardProtectionPolicy("owner-only","reader-password",new AccessPermission());protection.setEncryptionKeyLength(128);pdf.protect(protection);pdf.save(bytes);}
        var prepared=PreparedAttachment.of("private.pdf",bytes.toByteArray());assertEquals("ENCRYPTED",prepared.textStatus());assertTrue(prepared.text().isEmpty());assertArrayEquals(bytes.toByteArray(),prepared.content());
    }
    @ParameterizedTest @ValueSource(strings={"../x.txt","a/b.pdf","C:\\x.txt","CON.txt","payload.exe","a.txt.","a.txt ",".txt","a\nb\nx.txt"})
    void rejectsUnsafeNamesOrUnsupportedTypes(String name){assertThrows(BusinessException.class,()->PreparedAttachment.of(name,"data".getBytes(StandardCharsets.UTF_8)));}
    @Test void rejectsEmptyOversizeMismatchedAndExternalEntityDocuments()throws Exception{
        assertThrows(BusinessException.class,()->PreparedAttachment.of("a.txt",new byte[0]));assertThrows(BusinessException.class,()->PreparedAttachment.of("a.txt",new byte[PreparedAttachment.MAX_BYTES+1]));
        assertThrows(BusinessException.class,()->PreparedAttachment.of("a.pdf","not a pdf".getBytes()));assertThrows(BusinessException.class,()->PreparedAttachment.of("a.doc","not word".getBytes()));
        byte[] xml=docxXml("<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///definitely-not-read'>]><w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body><w:p><w:r><w:t>&secret;</w:t></w:r></w:p></w:body></w:document>");
        assertThrows(BusinessException.class,()->PreparedAttachment.of("attack.docx",xml));
    }
    @Test void tooLongTextIsNotSilentlyTruncatedAndByteArraysAreDefensive(){
        byte[] body="a".repeat(PreparedAttachment.MAX_TEXT+1).getBytes(StandardCharsets.UTF_8);var f=PreparedAttachment.of("long.txt",body);assertEquals("TEXT_TOO_LONG",f.textStatus());assertEquals("",f.text());assertArrayEquals(body,f.content());
        byte[] data="source".getBytes();var file=PreparedAttachment.of("source.txt",data);data[0]='x';byte[] returned=file.content();returned[0]='y';assertEquals("source",new String(file.content(),StandardCharsets.UTF_8));
    }
    @Test void atomicSaveVerifiesHashBeforeOverwritingExistingFile()throws Exception{
        var file=PreparedAttachment.of("source.txt","download bytes".getBytes(StandardCharsets.UTF_8));AttachmentInfo info=new AttachmentInfo(1,file.name(),file.mediaType(),file.size(),file.sha256(),file.textStatus(),LocalDateTime.now());Path target=directory.resolve("saved.txt");Files.writeString(target,"keep this");
        assertThrows(BusinessException.class,()->AttachmentFiles.save(target,new AttachmentDownload(info,"bad bytes".getBytes(),"")));assertEquals("keep this",Files.readString(target));
        AttachmentFiles.save(target,new AttachmentDownload(info,file.content(),file.text()));assertArrayEquals(file.content(),Files.readAllBytes(target));
    }
}
