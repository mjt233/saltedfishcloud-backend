import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

void main(String[] args) throws Exception {
    String baseDir = args.length > 0 ? args[0] : ".";
    Process process = new ProcessBuilder("git", "show", "master:pom.xml")
            .directory(new File(baseDir))
            .redirectErrorStream(true)
            .start();
    String xml = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0) {
        System.err.println("Failed to get pom.xml from master branch");
        System.exit(1);
    }
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    doc.getDocumentElement().normalize();
    String revision = doc.getDocumentElement()
            .getElementsByTagName("revision")
            .item(0)
            .getTextContent()
            .trim();
    System.out.print(revision);
}
