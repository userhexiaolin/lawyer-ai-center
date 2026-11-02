package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import java.io.ByteArrayInputStream;

/** Read list status from ZIP metadata, without loading POI, Word XML or XMLBeans schemas. */
final class TemplateStateReader {
    private static final String STATE_PART = "customXml/yulvxing-template-state.json";
    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final ObjectReader STATE = new ObjectMapper().readerFor(TemplateLayout.State.class);
    private TemplateStateReader() {}

    static TemplateLayout.State read(byte[] docx) {
        if (docx == null) throw damaged();
        try (var archive = ZipFile.builder().setSeekableByteChannel(new SeekableInMemoryByteChannel(docx)).get()) {
            if (archive.getEntry("word/document.xml") == null) throw damaged();
            // Validate the small package manifest; never parse the Word body, styles or field payload.
            var manifest = metadata(archive, "[Content_Types].xml");
            if (manifest == null) throw damaged();
            var factory = XMLInputFactory.newDefaultFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            var reader = factory.createXMLStreamReader(new ByteArrayInputStream(manifest));
            try {
                boolean root = false;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD) throw damaged();
                    if (event == XMLStreamConstants.START_ELEMENT && !root) {
                        if (!"Types".equals(reader.getLocalName()) || !"http://schemas.openxmlformats.org/package/2006/content-types".equals(reader.getNamespaceURI())) throw damaged();
                        root = true;
                    }
                }
                if (!root) throw damaged();
            } finally { reader.close(); }
            byte[] state = metadata(archive, STATE_PART);
            // Only valid legacy packages lacking a state part retain the existing legacy semantics.
            return state == null ? null : STATE.readValue(state);
        } catch (DraftingException e) { throw e; }
        catch (Exception e) { throw damaged(); }
    }

    private static byte[] metadata(ZipFile archive, String name) throws Exception {
        var matches = archive.getEntries(name).iterator();
        if (!matches.hasNext()) return null;
        var entry = matches.next();
        if (matches.hasNext() || entry.getSize() > MAX_METADATA_BYTES) throw damaged();
        try (var stream = archive.getInputStream(entry)) {
            byte[] bytes = stream.readNBytes(MAX_METADATA_BYTES + 1);
            if (bytes.length > MAX_METADATA_BYTES) throw damaged();
            return bytes;
        }
    }
    private static DraftingException damaged() { return new DraftingException("模板解析状态损坏，请重新上传", 422); }
}
