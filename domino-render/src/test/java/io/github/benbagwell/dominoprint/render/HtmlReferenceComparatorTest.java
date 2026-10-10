package io.github.benbagwell.dominoprint.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class HtmlReferenceComparatorTest {
    @Test void rendersPlainTableRowsWithTabbedCells(){var row=new RenderedNode("tr",Map.of(),"",List.of(new RenderedNode("td",Map.of(),"",List.of(new RenderedNode("p",Map.of(),"",List.of(RenderedNode.text("Quantity"))))),new RenderedNode("td",Map.of(),"",List.of(new RenderedNode("p",Map.of(),"",List.of(RenderedNode.text("3")))))));assertEquals("Quantity\t3\n",row.plainText());}
    @Test void distinguishesMarkupAndTextAgreement(){var c=HtmlReferenceComparator.compare("<html><head><title>ignored</title></head><body><div>Acme &amp; Sons</div></body></html>","<p>Acme &amp; Sons</p>");assertFalse(c.exactMarkup());assertTrue(c.normalizedTextMatches(),c.generatedText()+" vs "+c.referenceText());}
    @Test void preservesContentDifferences(){assertFalse(HtmlReferenceComparator.compare("<p>Approved</p>","<p>Declined</p>").normalizedTextMatches());assertEquals("logo",HtmlReferenceComparator.text("<img alt='logo'><script>unsafe()</script>"));}
}
