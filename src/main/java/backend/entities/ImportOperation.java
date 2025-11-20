package backend.entities;

import java.time.ZonedDateTime;

public class ImportOperation {
	private Long id;
	private String user;
	private String status;
	private ZonedDateTime createdAt;
	private Integer addedCount;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public ZonedDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(ZonedDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public Integer getAddedCount() {
		return addedCount;
	}

	public void setAddedCount(Integer addedCount) {
		this.addedCount = addedCount;
	}
}


