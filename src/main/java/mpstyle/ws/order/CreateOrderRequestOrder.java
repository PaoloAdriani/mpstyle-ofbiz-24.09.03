package mpstyle.ws.order;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "order")
@XmlAccessorType(XmlAccessType.FIELD)
public class CreateOrderRequestOrder 
{
	private String id;
    private double total;

    @XmlElement(name = "shipping-cost")
    private double shippingCost;

    @XmlElement(name = "invoice-required")
    private String InvoiceRequired;
    private String status;
    private CreateOrderRequestCustomer customer;
    
    @XmlElement(name = "delivery-address")
    private CreateOrderDeliveryAddress address;

    @XmlElementWrapper(name = "items")
    @XmlElement(name = "item")
    private List<CreateOrderRequestItem> items;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public double getTotal() {
		return total;
	}

	public void setTotal(double total) {
		this.total = total;
	}

	public double getShippingCost() {
		return shippingCost;
	}

	public void setShippingCost(double shippingCost) {
		this.shippingCost = shippingCost;
	}

	public String getInvoiceRequired() {
		return InvoiceRequired;
	}

	public void setInvoiceRequired(String invoiceRequired) {
		InvoiceRequired = invoiceRequired;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public CreateOrderRequestCustomer getCustomer() {
		return customer;
	}

	public void setCustomer(CreateOrderRequestCustomer customer) {
		this.customer = customer;
	}

	public CreateOrderDeliveryAddress getAddress() {
		return address;
	}

	public void setAddress(CreateOrderDeliveryAddress address) {
		this.address = address;
	}

	public List<CreateOrderRequestItem> getItems() {
		return items;
	}

	public void setItems(List<CreateOrderRequestItem> items) {
		this.items = items;
	}

	@Override
	public String toString() {
		return "CreateOrderRequestOrder [id=" + id + ", total=" + total + ", shippingCost=" + shippingCost
				+ ", InvoiceRequired=" + InvoiceRequired + ", status=" + status + ", customer=" + customer.toString()
				+ ", address=" + address.toString() + ", items=" + items.toString() + "]";
	}
    
}
