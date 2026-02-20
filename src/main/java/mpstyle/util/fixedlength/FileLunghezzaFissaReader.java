
package mpstyle.util.fixedlength;

import org.apache.ofbiz.base.util.Debug;

import java.io.*;

/**
 *
 * @author Nicola Mazzoni
 * 
 * 
 * Legge un file di testo database con record e campi a lughezza fissa e permette
 * di estrarne i valori in base alla posizione
 *
 *
 * <CODE>
 *
 * FileLunghezzaFissaReader r = new FileLunghezzaFissaReader("c:/prova.txt");
 *        while (r.readRecord()){
 *            System.out.println("Cliente ==>" + r.getCampo(1, 5));
 *            System.out.println("Stagione ==>" + r.getCampo(22, 2));
 *        }
 *
 * </CODE>
 */
public class FileLunghezzaFissaReader {
    
    public static final String module = FileLunghezzaFissaReader.class.getName();
    
    private char[] buffer = null;
    private LineNumberReader nr = null;
    
    public FileLunghezzaFissaReader(String pathFileInput) {
        
        try {
            FileReader fr = new FileReader(pathFileInput);
            nr = new LineNumberReader(fr);
        } catch (FileNotFoundException e) {
           Debug.logError("File ["+pathFileInput+"] not found.", module);
           Debug.logError(e.getMessage(), module);
        }
        
    }
    
    public void setLineNumber(int LineNumber) {
        
	try {
		nr.setLineNumber(LineNumber);
	} catch (Exception e) {
            Debug.logError(e.getMessage(), module);
		
	}
    }
    
    /**
     * 
     * @param posizione
     * @param lunghezza
     * @return 
     */
    public String getCampo(int posizione, int lunghezza) {
        
        posizione--;
        char[] campo = new char[lunghezza];
        
        for (int x=0; x< lunghezza;x++){
            campo[x] = buffer[posizione + x];
        }
        
        //Debug.logInfo("Posizione:" + posizione + " Lunghezza:" + lunghezza + " Campo:" + new String(campo), module);
        
        return new String(campo);
    }
    
    /**
     * 
     * @return 
     */
    public boolean readRecord() {
        
        try {
            
            String rec;
            rec = nr.readLine();
            
            if(rec == null) {
                return false;
            }
            
            for (int x=0; rec.length()<1000; x++){
                rec += " ";
            }
            
            buffer = rec.toCharArray();
            return true;
            
        } catch (IOException e) {
            Debug.logError(e.getMessage(), module);
            return false;
        }
    }
    
    
} //end class
