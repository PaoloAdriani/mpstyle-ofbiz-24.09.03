/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util.email;

/**
 *
 * @author equake58
 */
public class MpEmailUtil {
    
    public static final String module = MpEmailUtil.class.getName();
    
    /**
     * Returns an HTML-formatted centered title
     * @param bodyTitle
     * @return 
     */
    public static String createHtmlBodyTitle(String bodyTitle) {
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("<h2>").append("<center>").append(bodyTitle).append("</center>").append("</h2>");
        
        return sb.toString();
        
    }
    
    /**
     * Returns an HTML-formatted header text row
     * @param headerText
     * @return 
     */
    public static String createHtmlTextHeader(String headerText) {
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("<h3>").append("<b>").append(headerText).append("</b>").append("</h3>");
        
        return sb.toString();
    }
    
    /**
     * 
     * @param textLine
     * @return 
     */
    public static String createHtmlTextLine(String textLine) {
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("<p>").append(textLine).append("</p>");
        
        return sb.toString();
    }
    
    /**
     * 
     * @param listItem
     * @return 
     */
    public static String createHtmlListElement(String listItem) {
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("<li>").append(listItem).append("</li>");
        
        return sb.toString();
        
    }
    
}//end class
