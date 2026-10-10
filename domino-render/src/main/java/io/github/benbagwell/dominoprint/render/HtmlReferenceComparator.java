package io.github.benbagwell.dominoprint.render;

import java.io.*;
import java.util.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;

/** Structural smoke comparison. Text agreement is not evidence of visual layout agreement. */
public final class HtmlReferenceComparator {
    public record Comparison(boolean exactMarkup,boolean normalizedTextMatches,String generatedText,String referenceText) {}
    private HtmlReferenceComparator(){}
    public static Comparison compare(String generated,String reference){return new Comparison(generated.equals(reference),text(generated).equals(text(reference)),text(generated),text(reference));}
    public static String text(String html) {
        var out=new StringBuilder();
        try {new ParserDelegator().parse(new StringReader(html),new HTMLEditorKit.ParserCallback(){
            int suppressed;
            boolean hidden(HTML.Tag tag){return tag==HTML.Tag.HEAD||tag==HTML.Tag.SCRIPT||tag==HTML.Tag.STYLE;}
            boolean block(HTML.Tag tag){return Set.of(HTML.Tag.P,HTML.Tag.DIV,HTML.Tag.TR,HTML.Tag.TD,HTML.Tag.TH,HTML.Tag.H1,HTML.Tag.H2,HTML.Tag.H3,HTML.Tag.BR).contains(tag);}
            @Override public void handleStartTag(HTML.Tag tag,MutableAttributeSet attrs,int pos){if(hidden(tag))suppressed++;else if(suppressed==0&&block(tag))out.append(' ');}
            @Override public void handleEndTag(HTML.Tag tag,int pos){if(hidden(tag))suppressed=Math.max(0,suppressed-1);else if(suppressed==0&&block(tag))out.append(' ');}
            @Override public void handleSimpleTag(HTML.Tag tag,MutableAttributeSet attrs,int pos){if(suppressed==0){if(block(tag))out.append(' ');if(tag==HTML.Tag.IMG)out.append(Objects.toString(attrs.getAttribute(HTML.Attribute.ALT),""));}}
            @Override public void handleText(char[] chars,int pos){if(suppressed==0)out.append(chars);}
        },true);}catch(IOException e){throw new UncheckedIOException(e);}
        return out.toString().replace('\u00a0',' ').replaceAll("\\s+"," ").trim();
    }
}
