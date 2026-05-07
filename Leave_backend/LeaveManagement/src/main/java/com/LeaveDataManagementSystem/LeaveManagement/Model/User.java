package com.LeaveDataManagementSystem.LeaveManagement.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "Users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String name;
    private String password;
    private Set<String> roles;

    private String fullName;
    private String department;
    private List<String> otherDepartments;

    private String designation;
    private String joinDate;
    private String phoneNumber;
    private String address;
    private String dateOfBirth;
    private String gender;
    private String maritalStatus;
    private String employmentType;
    private String nationalId;
    private String emergencyContact;

    private Boolean canBeActingOfficer;
    private Boolean canBeApprovalOfficer;

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getId()          { return id; }
    public void setId(String id)   { this.id = id; }

    public String getEmail()             { return email; }
    public void setEmail(String email)   { this.email = email; }

    public String getName()              { return name; }
    public void setName(String name)     { this.name = name; }

    public String getPassword()                  { return password; }
    public void setPassword(String password)     { this.password = password; }

    public Set<String> getRoles()                { return roles; }
    public void setRoles(Set<String> roles)      { this.roles = roles; }

    public String getFullName()                  { return fullName; }
    public void setFullName(String fullName)     { this.fullName = fullName; }

    public String getDepartment()                { return department; }
    public void setDepartment(String dept)       { this.department = dept; }

    public List<String> getOtherDepartments()                       { return otherDepartments; }
    public void setOtherDepartments(List<String> otherDepartments)  { this.otherDepartments = otherDepartments; }

    public String getDesignation()               { return designation; }
    public void setDesignation(String d)         { this.designation = d; }

    public String getJoinDate()                  { return joinDate; }
    public void setJoinDate(String joinDate)     { this.joinDate = joinDate; }

    public String getPhoneNumber()               { return phoneNumber; }
    public void setPhoneNumber(String p)         { this.phoneNumber = p; }

    public String getAddress()                   { return address; }
    public void setAddress(String address)       { this.address = address; }

    public String getDateOfBirth()               { return dateOfBirth; }
    public void setDateOfBirth(String d)         { this.dateOfBirth = d; }

    public String getGender()                    { return gender; }
    public void setGender(String gender)         { this.gender = gender; }

    public String getMaritalStatus()             { return maritalStatus; }
    public void setMaritalStatus(String m)       { this.maritalStatus = m; }

    public String getEmploymentType()            { return employmentType; }
    public void setEmploymentType(String e)      { this.employmentType = e; }

    public String getNationalId()                { return nationalId; }
    public void setNationalId(String n)          { this.nationalId = n; }

    public String getEmergencyContact()          { return emergencyContact; }
    public void setEmergencyContact(String e)    { this.emergencyContact = e; }

    public Boolean getCanBeActingOfficer()               { return canBeActingOfficer; }
    public void setCanBeActingOfficer(Boolean b)         { this.canBeActingOfficer = b; }

    public Boolean getCanBeApprovalOfficer()             { return canBeApprovalOfficer; }
    public void setCanBeApprovalOfficer(Boolean b)       { this.canBeApprovalOfficer = b; }

    /**
     * Returns true if this user belongs to the given department
     * (primary or one of otherDepartments).
     */
    public boolean belongsToDepartment(String dept) {
        if (dept == null) return false;
        if (dept.equalsIgnoreCase(this.department)) return true;
        if (otherDepartments != null)
            return otherDepartments.stream().anyMatch(d -> d.equalsIgnoreCase(dept));
        return false;
    }
}