package mpstyle.ws.order;

public class CreateOrderRequestCustomer 
{
	private String id;
    private String name;
    private String address;
    private String address2;
    private String zip;
    private String city;
    private String vat;
    private String country;
    private String email;
    private String phone;
	
    public String getId() {
		return id;
	}
    
	public void setId(String id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
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
	
	public String getVat() {
		return vat;
	}
	
	public void setVat(String vat) {
		this.vat = vat;
	}
	
	public String getCountry() {
		return country;
	}
	
	public void setCountry(String country) {
		this.country = country;
	}
	
	public String getEmail() {
		return email;
	}
	
	public void setEmail(String email) {
		this.email = email;
	}
	
	public String getPhone() {
		return phone;
	}
	
	public void setPhone(String phone) {
		this.phone = phone;
	}

	@Override
	public String toString() {
		return "CreateOrderRequestCustomer [id=" + id + ", name=" + name + ", address=" + address + ", address2="
				+ address2 + ", zip=" + zip + ", city=" + city + ", vat=" + vat + ", country=" + country + ", email="
				+ email + ", phone=" + phone + "]";
	}
    
}
