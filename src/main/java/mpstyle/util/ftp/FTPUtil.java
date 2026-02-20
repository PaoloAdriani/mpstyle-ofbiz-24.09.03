/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util.ftp;

import org.apache.commons.net.ftp.FTPFile;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;

import java.util.ArrayList;
import java.util.List;


/**
 *
 * @author equake58
 */
public class FTPUtil {
    
    private final static String module = FTPUtil.class.getName();
    
    /**
     * 
     * 
     * @param ftpFiles Array of ftp files
     * @param filterPrefix String to check the ftp file names against: will be kept/removed
     *          the files with this prefix string. If this parameter is null all the 
     *          files will be kept/removed.
     * @param keep is true if you want to keep the files containing the "filterName" string;
     *          false if you want to keep all the files but the ones containing the
     *          "filterName" string.
     * @return filtered file list or an empty list no file match is found.
     */
    public static List<FTPFile> filterFTPFIlesByPrefixName(FTPFile[] ftpFiles, String filterPrefix, boolean keep) {
        
        List<FTPFile> filteredList = new ArrayList<>();
        
        if(filterPrefix == null) {
            
            if(keep) {
                filteredList.addAll(UtilMisc.toListArray(ftpFiles));
            }
            
            return filteredList;
        }
        
        
        if(ftpFiles.length > 0) {
            
            for(FTPFile file : ftpFiles) {
                
                String _filename = file.getName();
                Debug.logWarning("Checking file name -> "+_filename, module);
                
                if(keep) {
                
                    if(_filename.startsWith(filterPrefix)) {
                        if(!filteredList.contains(file)) {
                            filteredList.add(file);
                        }
                    }
                    
                }else{
                    //remove : keep only files that do not start with "filterPrefix"
                    if(!_filename.startsWith(filterPrefix)) {
                        if(!filteredList.contains(file)) {
                            filteredList.add(file);
                        }
                    }
                }
                
            }
            
            
        }else{
            Debug.logWarning("FTPFile array is empty; nothing to filter. Return empty list.", module);
        }
        
        
        return filteredList;
        
    }
    
} //end class
