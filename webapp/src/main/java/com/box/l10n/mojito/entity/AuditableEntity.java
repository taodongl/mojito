package com.box.l10n.mojito.entity;

import com.box.l10n.mojito.converter.JodaDateTimeJavaType;
import com.box.l10n.mojito.rest.View;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.annotations.VisibleForTesting;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import org.hibernate.annotations.JavaType;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * This class adds support for entity listener. It will track the creation date and modification
 * date of the entity extending this class.
 *
 * @author aloison
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends BaseEntity implements Serializable {

  @CreatedDate
  @Column(name = "created_date")
  @JavaType(JodaDateTimeJavaType.class)
  @JsonView(View.IdAndNameAndCreated.class)
  protected DateTime createdDate;

  @LastModifiedDate
  @Column(name = "last_modified_date")
  @JavaType(JodaDateTimeJavaType.class)
  @JsonView(View.Modified.class)
  protected DateTime lastModifiedDate;

  public DateTime getCreatedDate() {
    return createdDate;
  }

  public DateTime getLastModifiedDate() {
    return lastModifiedDate;
  }

  @VisibleForTesting
  public void setCreatedDate(DateTime createdDate) {
    this.createdDate = createdDate;
  }

  public void setLastModifiedDate(DateTime lastModifiedDate) {
    this.lastModifiedDate = lastModifiedDate;
  }
}
