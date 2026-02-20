/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util.file;

import mpstyle.edi.data.DataQOHErp;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 *
 * @author equake58
 */
public class MpFileUtil {
    
    public static final String MODULE = MpFileUtil.class.getName();
    
    /**
     * 
     * 
     * @param fileNamePathList List of file paths
     * @param filterSuffix String to check the ftp file names against: will be kept/removed
     *          the files with this suffix string. If this parameter is null all the 
     *          files will be kept/removed.
     * @param keep is true if you want to keep the files containing the "filterName" string;
     *          false if you want to keep all the files but the ones containing the
     *          "filterName" string.
     * @return filtered file list or an empty list no file match is found.
     */
    public static List<String> filterFileNamesBySuffix(List<String> fileNamePathList, String filterSuffix, boolean keep) {
        
        List<String> filteredList = new ArrayList<>();
        
        if(filterSuffix == null) {
            
            if(keep) {
                filteredList.addAll(fileNamePathList);
            }
            
            return filteredList;
        }
        
        
        if(fileNamePathList.size() > 0) {
            
            for(String filenamePath : fileNamePathList) {
                
                String filename = getFileNameFromFilePath(filenamePath);
                
                //String _filename = file.getName();
                Debug.logWarning("Checking file name -> "+filename, MODULE);
                
                if(keep) {
                
                    if(filename.endsWith(filterSuffix)) {
                        if(!filteredList.contains(filenamePath)) {
                            filteredList.add(filenamePath);
                        }
                    }
                    
                }else{
                    //remove : keep only files that do not start with "filterPrefix"
                    if(!filename.endsWith(filterSuffix)) {
                        if(!filteredList.contains(filenamePath)) {
                            filteredList.add(filenamePath);
                        }
                    }
                }
                
            }
            
            
        }else{
            Debug.logWarning("List of file names is empty; nothing to filter. Return empty list.", MODULE);
        }
        
        
        return filteredList;
        
    }
    
    /**
     * 
     * 
     * @param fileList List of files
     * @param filterSuffix String to check the ftp file names against: will be kept/removed
     *          the files with this suffix string. If this parameter is null all the 
     *          files will be kept/removed.
     * @param keep is true if you want to keep the files containing the "filterName" string;
     *          false if you want to keep all the files but the ones containing the
     *          "filterName" string.
     * @return filtered file list or an empty list no file match is found.
     */
    public static List<File> filterFilesBySuffix(List<File> fileList, String filterSuffix, boolean keep) {
        
        List<File> filteredList = new ArrayList<>();
        
        if(filterSuffix == null) {
            
            if(keep) {
                filteredList.addAll(fileList);
            }
            
            return filteredList;
        }
        
        
        if(fileList.size() > 0) {
            
            for(File file : fileList) {
                
                String _filename = file.getName();
                Debug.logWarning("Checking file name -> "+_filename, MODULE);
                
                if(keep) {
                
                    if(_filename.endsWith(filterSuffix)) {
                        if(!filteredList.contains(file)) {
                            filteredList.add(file);
                        }
                    }
                    
                }else{
                    //remove : keep only files that do not start with "filterPrefix"
                    if(!_filename.endsWith(filterSuffix)) {
                        if(!filteredList.contains(file)) {
                            filteredList.add(file);
                        }
                    }
                }
                
            }
            
            
        }else{
            Debug.logWarning("List of files is empty; nothing to filter. Return empty list.", MODULE);
        }
        
        
        return filteredList;
        
    }
    
    /**
     * 
     * 
     * @param fileNamePathList List of file paths
     * @param filterPrefix String to check the ftp file names against: will be kept/removed
     *          the files with this prefix string. If this parameter is null all the 
     *          files will be kept/removed.
     * @param keep is true if you want to keep the files containing the "filterName" string;
     *          false if you want to keep all the files but the ones containing the
     *          "filterName" string.
     * @return filtered file list or an empty list no file match is found.
     */
    public static List<String> filterFileNamesByPrefix(List<String> fileNamePathList, String filterPrefix, boolean keep) {
        
        List<String> filteredList = new ArrayList<>();
        
        if(filterPrefix == null) {
            
            if(keep) {
                filteredList.addAll(fileNamePathList);
            }
            
            return filteredList;
        }
        
        
        if(fileNamePathList.size() > 0) {
            
            for(String filenamePath : fileNamePathList) {
                
                String filename = getFileNameFromFilePath(filenamePath);
                
                //String _filename = file.getName();
                Debug.logWarning("Checking file name -> "+filename, MODULE);
                
                if(keep) {
                
                    if(filename.startsWith(filterPrefix)) {
                        if(!filteredList.contains(filenamePath)) {
                            filteredList.add(filenamePath);
                        }
                    }
                    
                }else{
                    //remove : keep only files that do not start with "filterPrefix"
                    if(!filename.startsWith(filterPrefix)) {
                        if(!filteredList.contains(filenamePath)) {
                            filteredList.add(filenamePath);
                        }
                    }
                }
                
            }
            
            
        }else{
            Debug.logWarning("List of file names is empty; nothing to filter. Return empty list.", MODULE);
        }
        
        
        return filteredList;
        
    }
    
      /**
     * 
     * 
     * @param fileList List of files
     * @param filterPrefix String to check the ftp file names against: will be kept/removed
     *          the files with this prefix string. If this parameter is null all the 
     *          files will be kept/removed.
     * @param keep is true if you want to keep the files containing the "filterName" string;
     *          false if you want to keep all the files but the ones containing the
     *          "filterName" string.
     * @return filtered file list or an empty list no file match is found.
     */
    public static List<File> filterFilesByPrefix(List<File> fileList, String filterPrefix, boolean keep) {
        
        List<File> filteredList = new ArrayList<>();
        
        if(filterPrefix == null) {
            
            if(keep) {
                filteredList.addAll(fileList);
            }
            
            return filteredList;
        }
        
        
        if(fileList.size() > 0) {
            
            for(File file : fileList) {
                
                String _filename = file.getName();
                Debug.logWarning("Checking file name -> "+_filename, MODULE);
                
                if(keep) {
                
                    if(_filename.endsWith(filterPrefix)) {
                        if(!filteredList.contains(file)) {
                            filteredList.add(file);
                        }
                    }
                    
                }else{
                    //remove : keep only files that do not start with "filterPrefix"
                    if(!_filename.endsWith(filterPrefix)) {
                        if(!filteredList.contains(file)) {
                            filteredList.add(file);
                        }
                    }
                }
                
            }
            
            
        }else{
            Debug.logWarning("List of files is empty; nothing to filter. Return empty list.", MODULE);
        }
        
        
        return filteredList;
        
    }
    
    /**
     * 
     * @param filename
     * @param splitByChar
     * @return 
     */
    public static List<DataQOHErp> readCvs(String filename, String splitByChar) {
        
        String line = "";
        //String cvsSplitBy = "\\|";
        List<DataQOHErp> arrayData = null;
        
        if(filename == null || UtilValidate.isEmpty(filename)) {
            Debug.logError("Filename is null or empty. Cannot read and split it.", MODULE);
            return null;
        }
        
        if(splitByChar == null || UtilValidate.isEmpty(splitByChar)) {
            Debug.logError("split character is null or empty. Cannot read and split file.", MODULE);
            return null;
        }
        
        arrayData = new ArrayList<>();
      
        try { 

            BufferedReader br = new BufferedReader(new FileReader(filename)); 

            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] data = line.split(splitByChar);

                if(data.length == 3) {
                    DataQOHErp lineData = new DataQOHErp(data[0].trim(),data[1].trim(),data[2].trim());
                    arrayData.add(lineData);
                }else{
                    Debug.logWarning("Found more than 3 fields in csv file [" + filename + "]. Abort.", MODULE);
                    return null;
                }

            }

            br.close();

        } catch (IOException e) {

            Debug.logError(e, e.getMessage(), MODULE);
            return null;
        }
	        
        return arrayData;
    }

    /**
     * Read content of a directory path and returns the absolute file path
     * for each file in the directory
     * @param path
     * @return 
     */
    public static ArrayList<String> readAndSort(String path) {
        
        if(path == null || path.isEmpty()) {
            Debug.logError("Path is null or empty. Cannot read and sort file list.", MODULE);
            return null;
        }
			
        ArrayList<String> sortedFileList = new ArrayList<>();
			 
        try {
				
            File folder = new File(path);

            File[] listOfFiles = folder.listFiles();

            for (File file : listOfFiles) {

                if (file.isFile()) {

                    sortedFileList.add(path + "" + file.getName()); 
                }

            }
			
            Collections.sort(sortedFileList);
			
            } catch (Exception e) {
                Debug.logError(e,e.getMessage(), MODULE);
                return null;
            }

            return sortedFileList;
			
    }
    
    /**
     * 
     * @param filePath
     * @return 
     */
    public static String getFileNameFromFilePath(String filePath) {
        
        String filename = null;
        
        if(filePath == null || filePath.trim().isEmpty()) {
            Debug.logError("File absolute path is null. Cannot retrieve file name", MODULE);
            return null;
            
        }
        
        File f = null;
        
        f = new File(filePath);
        if(f.exists()) {
            filename = f.getName();
        }
        
        return filename;
        
    }

    /**
     * Read all files which have the name that
     * start with a specific prefix on a defined path
     *
     * @param path
     * @param fileNamePath - file name prefix to filter IN
     * @return Arraylist of file name to read recursive
     */
    public static ArrayList<String> readWithPath(String path, String fileNamePath) {

        String ext = ".xml";
        ArrayList<String> filename = new ArrayList<>();

        if (path == null || path.trim().isEmpty()) {
            String msg = "Directory path is null or empty, cannot read files.";
            Debug.logError(msg, MODULE);
            return null;
        }

        if (fileNamePath == null || fileNamePath.trim().isEmpty()) {
            String msg = "File prefix is null or empty, cannot read files from directory [" + path + "]";
            Debug.logError(msg, MODULE);
            return null;
        }

        path = path.endsWith(File.separator) ? path : path + File.separator;

        try {
            File folder = new File(path);
            File[] listOfFiles = folder.listFiles();

            for (File file : listOfFiles) {
                if (file.exists() && file.isFile()) {

                    //Keep only the files that do not start with fileNamePath (prefix) and have the specific extension
                    if (file.getName().startsWith(fileNamePath) && file.getName().endsWith(ext)) {
                        filename.add(path + file.getName());
                    }
                }

            }

        } catch (Exception e) {
            String msg = "Error in reading files to import from directory [" + path + "]. Msg => " + e.getMessage();
            Debug.logError(msg, MODULE);
            e.printStackTrace();
        }

        return filename;

    }

    /**
     * Read all files which have the name that do not
     * start with a specific prefix on a defined path
     *
     * @param path
     * @param fileNamePath - file name prefix to filter OUT
     * @return Arraylist of file name to read recursive
     */
    public static ArrayList<String> readWithoutPath(String path, String fileNamePath) {

        String ext = ".xml";
        ArrayList<String> filename = new ArrayList<>();

        if (path == null || path.trim().isEmpty()) {
            String msg = "Directory path is null or empty, cannot read files.";
            Debug.logError(msg, MODULE);
            return null;
        }

        if (fileNamePath == null || fileNamePath.trim().isEmpty()) {
            String msg = "File prefix is null or empty, cannot read files from directory [" + path + "]";
            Debug.logError(msg, MODULE);
            return null;
        }

        path = path.endsWith(File.separator) ? path : path + File.separator;

        try {
            File folder = new File(path);
            File[] listOfFiles = folder.listFiles();

            for (File file : listOfFiles) {

                if (file.exists() && file.isFile()) {
                    //Keep only the files that do not start with fileNamePath (prefix) and have the specific extension
                    if (!(file.getName().startsWith(fileNamePath)) && file.getName().endsWith(ext)) {
                        filename.add(path + file.getName());
                    }
                }

            }

        } catch (Exception e) {
            String msg = "Error in reading files to import from directory [" + path + "]. Msg => " + e.getMessage();
            Debug.logError(msg, MODULE);
            e.printStackTrace();
        }

        return filename;

    }
    
    /**
     * 
     * @param source
     * @param destination
     * @return 
     */
    public static boolean moveToDirectory(String source, String destination) {
        
        if(source == null || source.trim().isEmpty() || destination == null || destination.trim().isEmpty()) {
            Debug.logError("Source and/or destination path missing. Cannot move file.", MODULE);
            return false;
        }
        
        File f = null;
        boolean moved = false;

        try {

            f = new File(source);

            Path s = Paths.get(source);
            Path d = Paths.get(destination + f.getName());

            Files.move(s, d, StandardCopyOption.ATOMIC_MOVE);
            moved = true;

        } catch (IOException e) {

            Debug.logError("Error in moving file [" + source + "]. Error is => " + e.getMessage(), MODULE);
            return false;
        }

        return moved;
    }
    
} //end class
