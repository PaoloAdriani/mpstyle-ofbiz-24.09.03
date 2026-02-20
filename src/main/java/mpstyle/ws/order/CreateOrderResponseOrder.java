/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.ws.order;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "order")
@XmlAccessorType(XmlAccessType.FIELD)
public class CreateOrderResponseOrder {

	private String id;
    private String status;
    private String text;
	    
	@XmlElement(name = "erp-order-id")
	private String ErpOrderId;

    @XmlElement(name = "status-description")
    private String statusDescription;
    
    
    @XmlElementWrapper(name = "items")
    @XmlElement(name = "item")
    private List<CreateOrderResponseItem> items;

    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getErpOrderId() {
        return ErpOrderId;
    }

    public void setErpOrderId(String ErpOrderId) {
        this.ErpOrderId = ErpOrderId;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }
   
    public List<CreateOrderResponseItem> getItems() {
		return items;
	}

	public void setItems(List<CreateOrderResponseItem> items) {
		this.items = items;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return "CreateOrderResponseOrder [id=" + id + ", status=" + status + ", text=" + text + ", ErpOrderId="
				+ ErpOrderId + ", statusDescription=" + statusDescription + ", items=" + items.toString() + "]";
	}
	
	
	
	
}
