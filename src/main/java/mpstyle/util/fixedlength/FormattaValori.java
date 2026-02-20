package mpstyle.util.fixedlength;

import org.apache.ofbiz.base.util.Debug;

import java.text.*;
import java.math.*;


/**
 *
 * @author Nicola Mazozni
 */
public class FormattaValori {
    
    public static final String module = FormattaValori.class.getName();
    
    
    public static String formattaDouble(String Maschera, double numero) {

        //if(numero==null){numero=0;}
        DecimalFormat df = new DecimalFormat(Maschera);
        String cos = df.format(numero);
        cos = cos.replace(',', '.');
        return cos;
    }
    
    public static String formattaInt(String Maschera, int numero){
	//if(numero==null){numero=0;}
        DecimalFormat df = new DecimalFormat(Maschera);
        String cos = df.format(numero);
        return cos;
    }
    
    
    public static String formattaDouble(String Maschera, String numero){
        try {
            double num = Double.parseDouble(numero);
            DecimalFormat df = new DecimalFormat(Maschera);
            String cos = df.format(num);
            cos = cos.replace(',', '.');
            return cos;
        } catch (NumberFormatException e){
            Debug.logError(e.getMessage(), module);
            return Maschera;
        }        
    }
    
    
    public static String ggmmaaaa2aaaammgg(String data){
        data = data.replaceAll("/", "");
        data = data.replaceAll("-", "");
        String gg = data.substring(0,2);
        String mm = data.substring(2,4);
        String aa = data.substring(4,8);
        int igg = Integer.parseInt(gg);
        int imm = Integer.parseInt(mm);
        int iaa = Integer.parseInt(aa);
        String faa = formattaDouble("0000", String.valueOf(iaa));
        String fmm= formattaDouble("00", String.valueOf(imm));
        String fgg = formattaDouble("00", String.valueOf(igg));
        String d = faa + fmm + fgg;
        return d;
    }
    
    public static String aaaammgg2ggmmaaaa(String data){
        String gg = data.substring(6,8);
        String mm = data.substring(4,6);
        String aa = data.substring(0,4);
        String d = gg + mm + aa;
        return d;
    }

    public static String Arrotonda(String data){
        
        double doubleVal = new BigDecimal(data).setScale(2, RoundingMode.HALF_UP).doubleValue();
        
        String val = Double.toString(doubleVal);
        
        return val;
        
    }
    
} //end class
