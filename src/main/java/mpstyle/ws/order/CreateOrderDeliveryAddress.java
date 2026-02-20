package mpstyle.ws.order;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "delivery-address")
public class CreateOrderDeliveryAddress 
{
	private String id;
    private String address;
    private String address2;
    private String zip;
    private String city;
    private String country;
	
    public String getId() {
		return id;
	}
    
	public void setId(String id) {
		this.id = id;
	}
	
	public String getAddress() {
		return address;
	}
	
	public void setAddress(String address) {
		this.address = address;
	}
	
	public String getAddress2() {
		return address2;
	}
	
	public void setAddress2(String address2) {
		this.address2 = address2;
	}
	
	public String getZip() {
		return zip;
	}
	
	public void setZip(String zip) {
		this.zip = zip;
	}
	
	public String getCity() {
		return city;
	}
	
	public void setCity(String city) {
		this.city = city;
	}
	
	public String getCountry() {
		return country;
	}
	
	public void setCountry(String country) {
		this.country = country;
	}
	
	@Override
	public String toString() {
		return "CreateOrderDeliveryAddress [id=" + id + ", address=" + address + ", address2=" + address2 + ", zip="
				+ zip + ", city=" + city + ", country=" + country + "]";
	}
    
}
