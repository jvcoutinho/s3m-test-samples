/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.value;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.text.StrBuilder;

import com.opengamma.engine.ComputationTargetSpecification;
import com.opengamma.engine.target.ComputationTargetReference;
import com.opengamma.engine.target.ComputationTargetRequirement;
import com.opengamma.engine.target.ComputationTargetType;
import com.opengamma.id.ExternalId;
import com.opengamma.id.ExternalIdBundle;
import com.opengamma.id.UniqueId;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.PublicAPI;

/**
 * An immutable requirement to obtain a value needed to perform a calculation.
 * <p>
 * This is a metadata-based requirement, and specifies only the minimal number of
 * parameters that are necessary to specify the user requirements.
 * <p>
 * The actual value which is computed is available as a {@link ValueSpecification}
 * that is capable of satisfying this requirement. A specification satisfies a requirement
 * if its properties satisfy the requirement constraints, plus the value name and
 * target specifications match.
 * <p>
 * This class is immutable and thread-safe.
 */
@PublicAPI
public final class ValueRequirement implements Serializable {

  /**
   * Default serial version ID
   */
  private static final long serialVersionUID = 1L;

  /**
   * The name of the value being requested.
   */
  private final String _valueName;

  /**
   * The object that the value refers to. This may be either a {@link ComputationTargetRequirement} or {@link ComputationTargetSpecification}. If the former, then it will be resolved into a stricter
   * form of value requirement during graph construction so that the target can be fully identified.
   */
  private final ComputationTargetReference _targetReference;

  /**
   * The constraints or additional parameters about the target.
   * For example, a currency constraint.
   */
  private final ValueProperties _constraints;

  /**
   * The cached hash code.
   */
  private transient volatile int _hashCode;
  
  /**
   * Creates a requirement with no value constraints.
   * <p>
   * This builds a {@link ComputationTargetSpecification} from the target type and id.
   * 
   * @param valueName  the value to load, not null
   * @param targetType  the target type, not null
   * @param targetId  the target identifier, may be null
   */
  public ValueRequirement(String valueName, ComputationTargetType targetType, UniqueId targetId) {
    this(valueName, new ComputationTargetSpecification(targetType, targetId));
  }

  /**
   * Creates a requirement with value constraints.
   * <p>
   * This builds a {@link ComputationTargetSpecification} from the target type and id.
   * 
   * @param valueName  the name of the value to load, not null
   * @param targetType  the target type, not null
   * @param targetId  the unique identifier of the target, not null
   * @param constraints  the value constraints that must be satisfied
   */
  public ValueRequirement(String valueName, ComputationTargetType targetType, UniqueId targetId, ValueProperties constraints) {
    this(valueName, new ComputationTargetSpecification(targetType, targetId), constraints);
  }

  /**
   * Creates a requirement with no value constraints.
   * <p>
   * This builds a {@link ComputationTargetRequirement} from the target type and id.
   * 
   * @param valueName the name of the value to load, not null
   * @param targetType the target type, not null
   * @param targetId the external identifier of the target, not null
   */
  public ValueRequirement(String valueName, ComputationTargetType targetType, ExternalId targetId) {
    this(valueName, new ComputationTargetRequirement(targetType, targetId));
  }

  /**
   * Creates a requirement with value constraints.
   * <p>
   * This builds a {@link ComputationTargetRequirement} from the target type and id.
   * 
   * @param valueName the name of the value to load, not null
   * @param targetType the target type, not null
   * @param targetId the external identifier of the target, not null
   * @param constraints the value constraints that must be satisfied
   */
  public ValueRequirement(String valueName, ComputationTargetType targetType, ExternalId targetId, ValueProperties constraints) {
    this(valueName, new ComputationTargetRequirement(targetType, targetId), constraints);
  }

  /**
   * Creates a requirement with no value constraints.
   * <p>
   * This builds a {@link ComputationTargetRequirement} from the target type and id bundle.
   * 
   * @param valueName the name of the value to load, not null
   * @param targetType the target type, not null
   * @param targetIds the external identifiers of the target, not null
   */
  public ValueRequirement(String valueName, ComputationTargetType targetType, ExternalIdBundle targetIds) {
    this(valueName, new ComputationTargetRequirement(targetType, targetIds));
  }

  /**
   * Creates a requirement with value constraints.
   * <p>
   * This builds a {@link ComputationTargetRequirement} from the target type and id bundle.
   * 
   * @param valueName the name of the value to load, not null
   * @param targetType the target type, not null
   * @param targetIds the external identifiers of the target, not null
   * @param constraints the value constraints that must be satisfied
   */
  public ValueRequirement(String valueName, ComputationTargetType targetType, ExternalIdBundle targetIds, ValueProperties constraints) {
    this(valueName, new ComputationTargetRequirement(targetType, targetIds), constraints);
  }

  /**
   * Creates a requirement from a target specification with no value constraints.
   * 
   * @param valueName the value to load, not null
   * @param targetReference the target reference, not null
   */
  public ValueRequirement(String valueName, ComputationTargetReference targetReference) {
    this(valueName, targetReference, ValueProperties.none());
  }

  /**
   * Creates a requirement from a target specification with value constraints.
   * 
   * @param valueName the name of the value to load, not null
   * @param targetReference the target specification, not null
   * @param constraints the value constraints that must be satisfied
   */
  public ValueRequirement(String valueName, ComputationTargetReference targetReference, ValueProperties constraints) {
    ArgumentChecker.notNull(valueName, "Value name");
    ArgumentChecker.notNull(targetReference, "Computation target specification");
    ArgumentChecker.notNull(constraints, "constraints");
    _valueName = getInterned(valueName);
    _targetReference = targetReference;
    _constraints = constraints;
  }

  private static final ConcurrentHashMap<String, String> s_interned = new ConcurrentHashMap<String, String>();
  
  public static String getInterned(String valueName) {
    //This has been observed to be faster if a large proportion of valueNames are already interned and we have a large number of cores
    String interned = s_interned.get(valueName);
    if (interned != null) {
      return interned;
    }
    interned = valueName.intern();
    s_interned.putIfAbsent(interned, interned); //NOTE: use interned for keys too
    return interned;
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the name of the value to load.
   * 
   * @return the valueName, not null
   */
  public String getValueName() {
    return _valueName;
  }

  /**
   * Gets the reference of the target that is to be loaded.
   * 
   * @return the target reference, not null
   */
  public ComputationTargetReference getTargetReference() {
    return _targetReference;
  }

  /**
   * Gets the constraints that must be satisfied.
   * 
   * @return the constraints, not null
   */
  public ValueProperties getConstraints() {
    return _constraints;
  }

  //-------------------------------------------------------------------------
  /**
   * Gets a specific constraint that must be specified.
   * <p>
   * If the constraint allows multiple specific values an arbitrary one is returned. 
   * 
   * @param constraintName  the constraint to query
   * @return the constraint value, null if it is not defined 
   * @throws IllegalArgumentException if the constraint is a wild-card definition
   */
  public String getConstraint(final String constraintName) {
    final Set<String> values = _constraints.getValues(constraintName);
    if (values == null) {
      return null;
    } else if (values.isEmpty()) {
      throw new IllegalArgumentException("constraint " + constraintName + " contains only wild-card values");
    } else {
      return values.iterator().next();
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof ValueRequirement) {
      ValueRequirement other = (ValueRequirement) obj;
      return _valueName == other._valueName && // values are interned
          _targetReference.equals(other._targetReference) &&
          _constraints.equals(other._constraints);
    }
    return false;
  }

  @Override
  public int hashCode() {
    if (_hashCode == 0) {
      final int prime = 31;
      int result = 1;
      result = prime * result + _valueName.hashCode();
      result = prime * result + _targetReference.hashCode();
      result = prime * result + _constraints.hashCode();
      _hashCode = result;
    }
    return _hashCode;
  }

  @Override
  public String toString() {
    return new StrBuilder().append("ValueReq[").append(getValueName()).append(", ").append(getTargetReference()).append(", ").append(getConstraints()).append(']').toString();
  }

}
