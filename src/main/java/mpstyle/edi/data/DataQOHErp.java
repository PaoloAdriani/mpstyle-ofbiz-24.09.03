package mpstyle.edi.data;

public class DataQOHErp {

    private String facility;
    private String article;
    private String availability;
	
    public DataQOHErp() {};

    public DataQOHErp(String facility, String article, String availability) {
            super();
            this.facility = facility;
            this.article = article;
            this.availability = availability;
    }
    public String getFacility() {
            return facility;
    }
    public void setFacility(String facility) {
            this.facility = facility;
    }
    public String getArticle() {
            return article;
    }
    public void setArticle(String article) {
            this.article = article;
    }
    public String getAvailability() {
            return availability;
    }
    public void setAvailability(String availability) {
            this.availability = availability;
    }
	

} //end class
