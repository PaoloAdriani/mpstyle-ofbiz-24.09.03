/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util.http;

import org.apache.ofbiz.base.util.Debug;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


/**
 *
 * @author equake58
 */
public class MpHttpUtil {
    
    public static final String MODULE = MpHttpUtil.class.getName();
    
    /**
     * Check if a resource URI exists and return true only if the response code 
     * is 200; false otherwise.
     * @param webResourceURI
     * @return 
     */
    public static boolean checkWebResourceURI(String webResourceURI) {
        
        boolean reosurceExists = false;
        
        if(webResourceURI == null) {
            return false;
        }
        
        try {
            
            
            URL url = new URL(webResourceURI);
            
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setInstanceFollowRedirects(true);
            huc.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
            huc.connect();
            
            int responseCode = huc.getResponseCode();
            
            //Debug.logWarning("Response code for resource [" + webResourceURI + "]: "+responseCode, MODULE);
            
            //resource exists only if response is 200
            if(responseCode == 200) {
                reosurceExists = true;
            }else{
                Debug.logError("Resource URI "+webResourceURI+" not found", MODULE);
            }
            
            
        } catch (MalformedURLException ex) {
            Debug.logError(ex.getMessage(), MODULE);
            return false;
        } catch (IOException ex) {
            Debug.logError(ex.getMessage(), MODULE);
            return false;
        }
        
        
        return reosurceExists;
    }
    
    
} //end class
