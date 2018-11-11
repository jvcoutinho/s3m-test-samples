/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.master.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.id.ExternalId;
import com.opengamma.id.ExternalIdBundle;
import com.opengamma.id.ExternalIdSearch;
import com.opengamma.id.ExternalIdSearchType;
import com.opengamma.id.ObjectId;
import com.opengamma.id.ObjectIdentifiable;
import com.opengamma.master.AbstractDocument;
import com.opengamma.master.AbstractSearchRequest;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.PublicSPI;
import com.opengamma.util.RegexUtils;

/**
 * Request for searching for users. 
 * <p>
 * Documents will be returned that match the search criteria.
 * This class provides the ability to page the results and to search
 * as at a specific version and correction instant.
 * See {@link UserHistoryRequest} for more details on how history works.
 */
@PublicSPI
@BeanDefinition
public class UserSearchRequest extends AbstractSearchRequest {

  /**
   * The set of user object identifiers, null to not limit by user object identifiers.
   * Note that an empty list will return no users.
   */
  @PropertyDefinition(set = "manual")
  private List<ObjectId> _objectIds;
  /**
   * The external user identifiers to match, null to not match on user identifiers.
   */
  @PropertyDefinition
  private ExternalIdSearch _externalIdSearch;
  /**
   * The external user identifier value, matching against the <b>value</b> of the identifiers,
   * null to not match by identifier value.
   * This matches against the {@link ExternalId#getValue() value} of the identifier
   * and does not match against the key. Wildcards are allowed.
   * This method is suitable for human searching, whereas the {@code externalIdSearch}
   * search is useful for exact machine searching.
   */
  @PropertyDefinition
  private String _externalIdValue;
  /**
   * The user id to search for, wildcards allowed, null to not match on name.
   */
  @PropertyDefinition
  private String _userId;
  /**
   * The display user name to search for, wildcards allowed, null to not match on name.
   */
  @PropertyDefinition
  private String _name;
  /**
   * The sort order to use.
   */
  @PropertyDefinition(validate = "notNull")
  private UserSearchSortOrder _sortOrder = UserSearchSortOrder.OBJECT_ID_ASC;

  /**
   * Creates an instance.
   */
  public UserSearchRequest() {
  }

  /**
   * Creates an instance using a single search identifier.
   * 
   * @param userId  the external user identifier to search for, not null
   */
  public UserSearchRequest(ExternalId userId) {
    addExternalId(userId);
  }

  /**
   * Creates an instance using a bundle of identifiers.
   * 
   * @param userIdBundle  the external user identifiers to search for, not null
   */
  public UserSearchRequest(ExternalIdBundle userIdBundle) {
    addExternalIds(userIdBundle);
  }

  //-------------------------------------------------------------------------
  /**
   * Adds a single user object identifier to the set.
   * 
   * @param userId  the user object identifier to add, not null
   */
  public void addObjectId(ObjectIdentifiable userId) {
    ArgumentChecker.notNull(userId, "userId");
    if (_objectIds == null) {
      _objectIds = new ArrayList<ObjectId>();
    }
    _objectIds.add(userId.getObjectId());
  }

  /**
   * Sets the set of user object identifiers, null to not limit by user object identifiers.
   * Note that an empty collection will return no securities.
   * 
   * @param userIds  the new user identifiers, null clears the user id search
   */
  public void setObjectIds(Iterable<? extends ObjectIdentifiable> userIds) {
    if (userIds == null) {
      _objectIds = null;
    } else {
      _objectIds = new ArrayList<ObjectId>();
      for (ObjectIdentifiable userId : userIds) {
        _objectIds.add(userId.getObjectId());
      }
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Adds a single external user identifier to the collection to search for.
   * Unless customized, the search will match 
   * {@link ExternalIdSearchType#ANY any} of the identifiers.
   * 
   * @param externalUserId  the external user identifier to add, not null
   */
  public void addExternalId(ExternalId externalUserId) {
    ArgumentChecker.notNull(externalUserId, "externalUserId");
    addExternalIds(Arrays.asList(externalUserId));
  }

  /**
   * Adds a collection of external user identifiers to the collection to search for.
   * Unless customized, the search will match 
   * {@link ExternalIdSearchType#ANY any} of the identifiers.
   * 
   * @param externalUserIds  the external user identifiers to add, not null
   */
  public void addExternalIds(ExternalId... externalUserIds) {
    ArgumentChecker.notNull(externalUserIds, "externalUserIds");
    if (getExternalIdSearch() == null) {
      setExternalIdSearch(new ExternalIdSearch(externalUserIds));
    } else {
      getExternalIdSearch().addExternalIds(externalUserIds);
    }
  }

  /**
   * Adds a collection of external user identifiers to the collection to search for.
   * Unless customized, the search will match 
   * {@link ExternalIdSearchType#ANY any} of the identifiers.
   * 
   * @param externalUserIds  the user key identifiers to add, not null
   */
  public void addExternalIds(Iterable<ExternalId> externalUserIds) {
    ArgumentChecker.notNull(externalUserIds, "externalUserIds");
    if (getExternalIdSearch() == null) {
      setExternalIdSearch(new ExternalIdSearch(externalUserIds));
    } else {
      getExternalIdSearch().addExternalIds(externalUserIds);
    }
  }

  //-------------------------------------------------------------------------
  @Override
  public boolean matches(AbstractDocument obj) {
    if (obj instanceof UserDocument == false) {
      return false;
    }
    UserDocument document = (UserDocument) obj;
    ManageableOGUser user = document.getUser();
    if (getObjectIds() != null && getObjectIds().contains(document.getObjectId()) == false) {
      return false;
    }
    if (getExternalIdSearch() != null && getExternalIdSearch().matches(user.getExternalIdBundle()) == false) {
      return false;
    }
    if (getUserId() != null && RegexUtils.wildcardMatch(getUserId(), user.getUserId()) == false) {
      return false;
    }
    if (getName() != null && RegexUtils.wildcardMatch(getName(), user.getName()) == false) {
      return false;
    }
    if (getExternalIdValue() != null) {
      for (ExternalId identifier : user.getExternalIdBundle()) {
        if (RegexUtils.wildcardMatch(getExternalIdValue(), identifier.getValue()) == false) {
          return false;
        }
      }
    }
    return true;
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code UserSearchRequest}.
   * @return the meta-bean, not null
   */
  public static UserSearchRequest.Meta meta() {
    return UserSearchRequest.Meta.INSTANCE;
  }
  static {
    JodaBeanUtils.registerMetaBean(UserSearchRequest.Meta.INSTANCE);
  }

  @Override
  public UserSearchRequest.Meta metaBean() {
    return UserSearchRequest.Meta.INSTANCE;
  }

  @Override
  protected Object propertyGet(String propertyName, boolean quiet) {
    switch (propertyName.hashCode()) {
      case -1489617159:  // objectIds
        return getObjectIds();
      case -265376882:  // externalIdSearch
        return getExternalIdSearch();
      case 2072311499:  // externalIdValue
        return getExternalIdValue();
      case -836030906:  // userId
        return getUserId();
      case 3373707:  // name
        return getName();
      case -26774448:  // sortOrder
        return getSortOrder();
    }
    return super.propertyGet(propertyName, quiet);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void propertySet(String propertyName, Object newValue, boolean quiet) {
    switch (propertyName.hashCode()) {
      case -1489617159:  // objectIds
        setObjectIds((List<ObjectId>) newValue);
        return;
      case -265376882:  // externalIdSearch
        setExternalIdSearch((ExternalIdSearch) newValue);
        return;
      case 2072311499:  // externalIdValue
        setExternalIdValue((String) newValue);
        return;
      case -836030906:  // userId
        setUserId((String) newValue);
        return;
      case 3373707:  // name
        setName((String) newValue);
        return;
      case -26774448:  // sortOrder
        setSortOrder((UserSearchSortOrder) newValue);
        return;
    }
    super.propertySet(propertyName, newValue, quiet);
  }

  @Override
  protected void validate() {
    JodaBeanUtils.notNull(_sortOrder, "sortOrder");
    super.validate();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      UserSearchRequest other = (UserSearchRequest) obj;
      return JodaBeanUtils.equal(getObjectIds(), other.getObjectIds()) &&
          JodaBeanUtils.equal(getExternalIdSearch(), other.getExternalIdSearch()) &&
          JodaBeanUtils.equal(getExternalIdValue(), other.getExternalIdValue()) &&
          JodaBeanUtils.equal(getUserId(), other.getUserId()) &&
          JodaBeanUtils.equal(getName(), other.getName()) &&
          JodaBeanUtils.equal(getSortOrder(), other.getSortOrder()) &&
          super.equals(obj);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash += hash * 31 + JodaBeanUtils.hashCode(getObjectIds());
    hash += hash * 31 + JodaBeanUtils.hashCode(getExternalIdSearch());
    hash += hash * 31 + JodaBeanUtils.hashCode(getExternalIdValue());
    hash += hash * 31 + JodaBeanUtils.hashCode(getUserId());
    hash += hash * 31 + JodaBeanUtils.hashCode(getName());
    hash += hash * 31 + JodaBeanUtils.hashCode(getSortOrder());
    return hash ^ super.hashCode();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the set of user object identifiers, null to not limit by user object identifiers.
   * Note that an empty list will return no users.
   * @return the value of the property
   */
  public List<ObjectId> getObjectIds() {
    return _objectIds;
  }

  /**
   * Gets the the {@code objectIds} property.
   * Note that an empty list will return no users.
   * @return the property, not null
   */
  public final Property<List<ObjectId>> objectIds() {
    return metaBean().objectIds().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the external user identifiers to match, null to not match on user identifiers.
   * @return the value of the property
   */
  public ExternalIdSearch getExternalIdSearch() {
    return _externalIdSearch;
  }

  /**
   * Sets the external user identifiers to match, null to not match on user identifiers.
   * @param externalIdSearch  the new value of the property
   */
  public void setExternalIdSearch(ExternalIdSearch externalIdSearch) {
    this._externalIdSearch = externalIdSearch;
  }

  /**
   * Gets the the {@code externalIdSearch} property.
   * @return the property, not null
   */
  public final Property<ExternalIdSearch> externalIdSearch() {
    return metaBean().externalIdSearch().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the external user identifier value, matching against the <b>value</b> of the identifiers,
   * null to not match by identifier value.
   * This matches against the {@link ExternalId#getValue() value} of the identifier
   * and does not match against the key. Wildcards are allowed.
   * This method is suitable for human searching, whereas the {@code externalIdSearch}
   * search is useful for exact machine searching.
   * @return the value of the property
   */
  public String getExternalIdValue() {
    return _externalIdValue;
  }

  /**
   * Sets the external user identifier value, matching against the <b>value</b> of the identifiers,
   * null to not match by identifier value.
   * This matches against the {@link ExternalId#getValue() value} of the identifier
   * and does not match against the key. Wildcards are allowed.
   * This method is suitable for human searching, whereas the {@code externalIdSearch}
   * search is useful for exact machine searching.
   * @param externalIdValue  the new value of the property
   */
  public void setExternalIdValue(String externalIdValue) {
    this._externalIdValue = externalIdValue;
  }

  /**
   * Gets the the {@code externalIdValue} property.
   * null to not match by identifier value.
   * This matches against the {@link ExternalId#getValue() value} of the identifier
   * and does not match against the key. Wildcards are allowed.
   * This method is suitable for human searching, whereas the {@code externalIdSearch}
   * search is useful for exact machine searching.
   * @return the property, not null
   */
  public final Property<String> externalIdValue() {
    return metaBean().externalIdValue().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the user id to search for, wildcards allowed, null to not match on name.
   * @return the value of the property
   */
  public String getUserId() {
    return _userId;
  }

  /**
   * Sets the user id to search for, wildcards allowed, null to not match on name.
   * @param userId  the new value of the property
   */
  public void setUserId(String userId) {
    this._userId = userId;
  }

  /**
   * Gets the the {@code userId} property.
   * @return the property, not null
   */
  public final Property<String> userId() {
    return metaBean().userId().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the display user name to search for, wildcards allowed, null to not match on name.
   * @return the value of the property
   */
  public String getName() {
    return _name;
  }

  /**
   * Sets the display user name to search for, wildcards allowed, null to not match on name.
   * @param name  the new value of the property
   */
  public void setName(String name) {
    this._name = name;
  }

  /**
   * Gets the the {@code name} property.
   * @return the property, not null
   */
  public final Property<String> name() {
    return metaBean().name().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the sort order to use.
   * @return the value of the property, not null
   */
  public UserSearchSortOrder getSortOrder() {
    return _sortOrder;
  }

  /**
   * Sets the sort order to use.
   * @param sortOrder  the new value of the property, not null
   */
  public void setSortOrder(UserSearchSortOrder sortOrder) {
    JodaBeanUtils.notNull(sortOrder, "sortOrder");
    this._sortOrder = sortOrder;
  }

  /**
   * Gets the the {@code sortOrder} property.
   * @return the property, not null
   */
  public final Property<UserSearchSortOrder> sortOrder() {
    return metaBean().sortOrder().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code UserSearchRequest}.
   */
  public static class Meta extends AbstractSearchRequest.Meta {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code objectIds} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<List<ObjectId>> _objectIds = DirectMetaProperty.ofReadWrite(
        this, "objectIds", UserSearchRequest.class, (Class) List.class);
    /**
     * The meta-property for the {@code externalIdSearch} property.
     */
    private final MetaProperty<ExternalIdSearch> _externalIdSearch = DirectMetaProperty.ofReadWrite(
        this, "externalIdSearch", UserSearchRequest.class, ExternalIdSearch.class);
    /**
     * The meta-property for the {@code externalIdValue} property.
     */
    private final MetaProperty<String> _externalIdValue = DirectMetaProperty.ofReadWrite(
        this, "externalIdValue", UserSearchRequest.class, String.class);
    /**
     * The meta-property for the {@code userId} property.
     */
    private final MetaProperty<String> _userId = DirectMetaProperty.ofReadWrite(
        this, "userId", UserSearchRequest.class, String.class);
    /**
     * The meta-property for the {@code name} property.
     */
    private final MetaProperty<String> _name = DirectMetaProperty.ofReadWrite(
        this, "name", UserSearchRequest.class, String.class);
    /**
     * The meta-property for the {@code sortOrder} property.
     */
    private final MetaProperty<UserSearchSortOrder> _sortOrder = DirectMetaProperty.ofReadWrite(
        this, "sortOrder", UserSearchRequest.class, UserSearchSortOrder.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> _metaPropertyMap$ = new DirectMetaPropertyMap(
      this, (DirectMetaPropertyMap) super.metaPropertyMap(),
        "objectIds",
        "externalIdSearch",
        "externalIdValue",
        "userId",
        "name",
        "sortOrder");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1489617159:  // objectIds
          return _objectIds;
        case -265376882:  // externalIdSearch
          return _externalIdSearch;
        case 2072311499:  // externalIdValue
          return _externalIdValue;
        case -836030906:  // userId
          return _userId;
        case 3373707:  // name
          return _name;
        case -26774448:  // sortOrder
          return _sortOrder;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends UserSearchRequest> builder() {
      return new DirectBeanBuilder<UserSearchRequest>(new UserSearchRequest());
    }

    @Override
    public Class<? extends UserSearchRequest> beanType() {
      return UserSearchRequest.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return _metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code objectIds} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<List<ObjectId>> objectIds() {
      return _objectIds;
    }

    /**
     * The meta-property for the {@code externalIdSearch} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ExternalIdSearch> externalIdSearch() {
      return _externalIdSearch;
    }

    /**
     * The meta-property for the {@code externalIdValue} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> externalIdValue() {
      return _externalIdValue;
    }

    /**
     * The meta-property for the {@code userId} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> userId() {
      return _userId;
    }

    /**
     * The meta-property for the {@code name} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> name() {
      return _name;
    }

    /**
     * The meta-property for the {@code sortOrder} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<UserSearchSortOrder> sortOrder() {
      return _sortOrder;
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
