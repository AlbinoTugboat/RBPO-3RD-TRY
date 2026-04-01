package ru.mtuci.coursemanagement.controller;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
public class XmlController {
    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping(value = "/api/xml/parse", consumes = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE})
    public String parse(@RequestBody String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            if (document.getDocumentElement() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "XML root element is missing");
            }
            return document.getDocumentElement().getTextContent();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid XML payload");
        }
    }
}
