import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.File;

void main(String[] args) throws Exception {
    String baseDir = args.length > 0 ? args[0] : ".";
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(new File(baseDir, "pom.xml"));
    doc.getDocumentElement().normalize();
    String revision = doc.getDocumentElement()
            .getElementsByTagName("revision")
            .item(0)
            .getTextContent()
            .trim();
    System.out.print(revision);
}
