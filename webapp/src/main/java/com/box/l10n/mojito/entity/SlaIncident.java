package com.box.l10n.mojito.entity;

import java.util.Set;

import com.box.l10n.mojito.converter.JodaDateTimeJavaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

/** @author jaurambault */
@Entity
@Table(name = "sla_incident")
public class SlaIncident extends AuditableEntity {

  @Column(name = "closed_date")
  @JavaType(JodaDateTimeJavaType.class)
  private DateTime closedDate;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "sla_incident_repositories",
      joinColumns = {@JoinColumn(name = "sla_incident_id")},
      inverseJoinColumns = {@JoinColumn(name = "repository_id")},
      foreignKey = @ForeignKey(name = "FK__SLA_INCIDENT_REPOSITORIES__SLA_INCIDENT__ID"),
      inverseForeignKey = @ForeignKey(name = "FK__SLA_INCIDENT_REPOSITORIES__REPOSITORY__ID"))
  private Set<Repository> repositories;

  public Set<Repository> getRepositories() {
    return repositories;
  }

  public void setRepositories(Set<Repository> repositories) {
    this.repositories = repositories;
  }

  public DateTime getClosedDate() {
    return closedDate;
  }

  public void setClosedDate(DateTime closedDate) {
    this.closedDate = closedDate;
  }
}
