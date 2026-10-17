package edu.course.peerreview.attachment;

import edu.course.peerreview.service.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.zip.*;

/** Bounded text extraction only: no Office automation, macros, document execution or network entities. */
public final class DocumentText {
    private DocumentText() { }
    public record Result(String text,String status) { }
    public static Result extract(String extension,byte[] bytes){
        try{
            String text=switch(extension){
                case "txt","md" -> text(bytes);
                case "docx" -> docx(bytes);
                case "doc" -> doc(bytes);
                case "pdf" -> pdf(bytes);
                default -> throw new BusinessException("不支持的文档格式。");
            };
            if(text.startsWith("﻿"))text=text.substring(1);
            text=text.strip();return text.isEmpty()?new Result("","NO_TEXT"):text.length()>PreparedAttachment.MAX_TEXT?new Result("","TEXT_TOO_LONG"):new Result(text,"EXTRACTED");
        }catch(TooLong e){return new Result("","TEXT_TOO_LONG");}
        catch(InvalidPasswordException e){return new Result("","ENCRYPTED");}
        catch(BusinessException e){throw e;}
        catch(Exception e){throw new BusinessException("文档损坏、格式与扩展名不匹配，或内容无法安全读取；请重新导出文件。");}
    }
    private static String text(byte[] bytes)throws Exception{
        if(bytes.length>=2&&((bytes[0]==(byte)0xFF&&bytes[1]==(byte)0xFE)||(bytes[0]==(byte)0xFE&&bytes[1]==(byte)0xFF)))
            return StandardCharsets.UTF_16.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        for(byte b:bytes)if(b==0)throw new BusinessException("文本文件包含二进制内容。");
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){return Charset.forName("GB18030").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
    }
    private static String docx(byte[] bytes)throws Exception{
        if(bytes.length<4||bytes[0]!='P'||bytes[1]!='K')throw new BusinessException("不是有效 DOCX 文件。");
        Path temp=Files.createTempFile("peer-review-docx-",".zip");
        try{
            Files.write(temp,bytes);
            try(ZipFile zip=new ZipFile(temp.toFile())){
                if(zip.size()>1000)throw new BusinessException("文档内部文件数量过多，拒绝解析。");
                if(zip.getEntry("[Content_Types].xml")==null||zip.getEntry("_rels/.rels")==null)throw new BusinessException("DOCX 缺少标准文档包结构。");
                if(zip.getEntry("word/vbaProject.bin")!=null)throw new BusinessException("不接受包含宏的 DOCX 文件。");
                ZipEntry entry=zip.getEntry("word/document.xml");if(entry==null)throw new BusinessException("DOCX 缺少正文结构。");
                if(entry.getSize()>4*1024*1024)throw new TooLong();
                byte[] xml;try(InputStream in=zip.getInputStream(entry)){xml=in.readNBytes(4*1024*1024+1);}if(xml.length>4*1024*1024)throw new TooLong();
                DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
                var builder=factory.newDocumentBuilder();builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){@Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}});
                var document=builder.parse(new ByteArrayInputStream(xml));var paragraphs=document.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","p");StringBuilder text=new StringBuilder();
                for(int i=0;i<paragraphs.getLength();i++){
                    var parts=((org.w3c.dom.Element)paragraphs.item(i)).getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","t");
                    for(int j=0;j<parts.getLength();j++){text.append(parts.item(j).getTextContent());if(text.length()>PreparedAttachment.MAX_TEXT)throw new TooLong();}text.append('\n');
                }return text.toString();
            }
        }finally{Files.deleteIfExists(temp);}
    }
    private static String doc(byte[] bytes)throws Exception{
        byte[] magic={(byte)0xD0,(byte)0xCF,0x11,(byte)0xE0,(byte)0xA1,(byte)0xB1,0x1A,(byte)0xE1};
        if(bytes.length<magic.length)throw new BusinessException("不是有效 DOC 文件。");for(int i=0;i<magic.length;i++)if(bytes[i]!=magic[i])throw new BusinessException("不是有效 DOC 文件。");
        try(HWPFDocument document=new HWPFDocument(new ByteArrayInputStream(bytes));WordExtractor extractor=new WordExtractor(document)){return extractor.getText();}
    }
    private static String pdf(byte[] bytes)throws Exception{
        if(bytes.length<5||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw new BusinessException("不是有效 PDF 文件。");
        try(var document=Loader.loadPDF(bytes)){
            if(document.getNumberOfPages()>100)throw new TooLong();
            StringBuilder text=new StringBuilder();
            Writer writer=new Writer(){public void write(char[] chars,int off,int len){if(text.length()+len>PreparedAttachment.MAX_TEXT)throw new TextLimit();text.append(chars,off,len);}public void flush(){}public void close(){}};
            try{new PDFTextStripper().writeText(document,writer);}catch(TextLimit e){throw new TooLong();}return text.toString();
        }
    }
    private static final class TooLong extends Exception { }
    private static final class TextLimit extends RuntimeException { }
}
