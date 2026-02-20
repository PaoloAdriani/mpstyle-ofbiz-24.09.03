/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util.xml;

import java.io.Writer;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 *
 * @author equake58
 */
public class XMLUtil {
    
    public static final String module = XMLUtil.class.getName();
    
    /**
     * Create an XML element for an XML document.
     * @param doc - XML Document
     * @param elementName - element/tag name 
     * @param elementAttributes element/tag attributes
     * @param textContent - element/tag text content
     * @return 
     */
    public static Element createXMLElement(Document doc, String elementName, Map<String, String> elementAttributes, String textContent) {
        
        Element elem = null;
        
        if(doc != null) {
            
            if(elementName != null) {
            
                elem = doc.createElement(elementName);

                if(UtilValidate.isNotEmpty(elementAttributes)) {

                    for(Entry<String, String> _entry : elementAttributes.entrySet()) {

                        String _attrName = _entry.getKey();
                        String _attrValue = _entry.getValue();

                        elem.setAttribute(_attrName, _attrValue);

                    }

                }
                
                //Set a text content if any
                if(UtilValidate.isNotEmpty(textContent)) {
                    elem.setTextContent(textContent);
                }
                
            }else{
                Debug.logError("Cannot create Element. Element name is null.", module);
                return null;
            }
            
        }
        
        return elem;
        
    }
    
    /**
     * 
     * @return 
     */
    public static Document createDocument() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.newDocument();
        } catch (ParserConfigurationException ex) {
            Debug.logError(ex, module);
        }
        return null;
    }

    /**
     * 
     * @param d
     * @param w 
     */
    public static void saveDocument(Document d, Writer w) {
        DOMImplementation di = d.getImplementation();
        DOMImplementationLS dils = (DOMImplementationLS) di;

        LSSerializer lss = dils.createLSSerializer();
        LSOutput lso = dils.createLSOutput();

        lso.setCharacterStream(w);
        lso.setEncoding("UTF-8");
        lss.getDomConfig().setParameter("format-pretty-print", true);

        lss.write(d, lso);

    }
    
    
}//end class
