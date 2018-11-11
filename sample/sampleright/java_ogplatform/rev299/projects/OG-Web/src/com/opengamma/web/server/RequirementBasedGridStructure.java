/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.web.server;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.engine.ComputationTargetSpecification;
import com.opengamma.engine.target.ComputationTargetType;
import com.opengamma.engine.value.ValueProperties;
import com.opengamma.engine.value.ValuePropertyNames;
import com.opengamma.engine.value.ValueSpecification;
import com.opengamma.engine.view.compilation.CompiledViewCalculationConfiguration;
import com.opengamma.engine.view.compilation.CompiledViewDefinition;
import com.opengamma.util.tuple.Pair;

/**
 * Encapsulates the structure of a grid, and the mapping from output values to rows and columns.
 */
public class RequirementBasedGridStructure {

  private static final Logger s_logger = LoggerFactory.getLogger(RequirementBasedGridStructure.class);

  /** Map of target to row index. */
  private final Map<ComputationTargetSpecification, int[]> _targetIdMap;
  private final ComputationTargetSpecification[] _targets;
  private final List<WebViewGridColumn> _orderedColumns;
  private final Map<RequirementBasedColumnKey, Collection<WebViewGridColumn>> _specificationBasedColumns;
  private final Map<Integer, Set<Integer>> _unsatisfiedCells;

  public RequirementBasedGridStructure(CompiledViewDefinition compiledViewDefinition, Set<? extends ComputationTargetType> targetTypes,
      List<RequirementBasedColumnKey> requirements, List<ComputationTargetSpecification> targets) {
    ValueSpecificationAnalysisResult analysisResult = analyseValueSpecifications(compiledViewDefinition, requirements, targetTypes, targets);
    Map<RequirementBasedColumnKey, Collection<WebViewGridColumn>> specificationBasedColumns = new HashMap<RequirementBasedColumnKey, Collection<WebViewGridColumn>>();
    Map<RequirementBasedColumnKey, WebViewGridColumn> requirementBasedColumns = new HashMap<RequirementBasedColumnKey, WebViewGridColumn>();
    List<WebViewGridColumn> orderedColumns = new ArrayList<WebViewGridColumn>();

    // Generate columns in correct order
    int colId = 0;
    for (RequirementBasedColumnKey requirement : requirements) {
      if (requirementBasedColumns.containsKey(requirement)) {
        continue;
      }
      // Not seen the requirement before - generate a column
      String columnHeader = getColumnHeader(requirement);
      String columnDescription = getColumnDescription(requirement);
      WebViewGridColumn column = new WebViewGridColumn(colId++, columnHeader, columnDescription, requirement.getValueName());
      requirementBasedColumns.put(requirement, column);
      orderedColumns.add(column);
    }

    for (Map.Entry<RequirementBasedColumnKey, Collection<RequirementBasedColumnKey>> specToRequirement : analysisResult.getSpecificationToRequirements().entrySet()) {
      RequirementBasedColumnKey specificationBasedKey = specToRequirement.getKey();
      Collection<RequirementBasedColumnKey> requirementBasedKeys = specToRequirement.getValue();

      // Turn requirements into columns
      Collection<WebViewGridColumn> columns = specificationBasedColumns.get(specificationBasedKey);
      if (columns == null) {
        columns = new ArrayList<WebViewGridColumn>();
        specificationBasedColumns.put(specificationBasedKey, columns);
      }
      for (RequirementBasedColumnKey requirementBasedKey : requirementBasedKeys) {
        WebViewGridColumn column = requirementBasedColumns.get(requirementBasedKey);
        if (column == null) {
          s_logger.warn("No column found for requirement {}", requirementBasedKey);
          continue;
        }
        columns.add(column);
      }
    }

    _specificationBasedColumns = specificationBasedColumns;
    _orderedColumns = orderedColumns;

    // Order of targets could be important, so use a linked map
    if (targets == null) {
      targets = analysisResult.getTargets();
    }
    _targetIdMap = new LinkedHashMap<ComputationTargetSpecification, int[]>();
    _unsatisfiedCells = new HashMap<Integer, Set<Integer>>();
    int nextId = 0;
    for (ComputationTargetSpecification target : targets) {
      final int targetRowId = nextId++;
      final int[] ids = _targetIdMap.get(target);
      if (ids == null) {
        _targetIdMap.put(target, new int[] {targetRowId });
      } else {
        final int[] newIds = new int[ids.length + 1];
        System.arraycopy(ids, 0, newIds, 0, ids.length);
        newIds[ids.length] = targetRowId;
        _targetIdMap.put(target, newIds);
      }
      Set<RequirementBasedColumnKey> missingColumnKeys = analysisResult.getUnsatisfiedRequirements(target);
      if (missingColumnKeys == null) {
        continue;
      }
      IntSet missingColumnIds = new IntArraySet();
      for (RequirementBasedColumnKey requirementBasedKey : missingColumnKeys) {
        WebViewGridColumn missingGridColumn = requirementBasedColumns.get(requirementBasedKey);
        if (missingGridColumn == null) {
          continue;
        }
        missingColumnIds.add(missingGridColumn.getId());
      }
      _unsatisfiedCells.put(targetRowId, missingColumnIds);
    }
    _targets = new ComputationTargetSpecification[nextId];
    for (ComputationTargetSpecification target : targets) {
      for (int rowId : _targetIdMap.get(target)) {
        _targets[rowId] = target;
      }
    }
  }

  private static ValueSpecificationAnalysisResult analyseValueSpecifications(CompiledViewDefinition compiledViewDefinition,
      Collection<RequirementBasedColumnKey> requirements, Set<? extends ComputationTargetType> targetTypes, List<ComputationTargetSpecification> targets) {
    Map<Pair<String, String>, Set<RequirementBasedColumnKey>> requirementsByConfigValueName = getRequirementsMap(requirements);
    Set<ComputationTargetSpecification> impliedTargets = targets == null ? new HashSet<ComputationTargetSpecification>() : null;
    Map<RequirementBasedColumnKey, Collection<RequirementBasedColumnKey>> specToRequirements = new HashMap<RequirementBasedColumnKey, Collection<RequirementBasedColumnKey>>();
    Map<RequirementBasedColumnKey, Set<ComputationTargetSpecification>> specToTargets = new HashMap<RequirementBasedColumnKey, Set<ComputationTargetSpecification>>();

    for (CompiledViewCalculationConfiguration compiledCalcConfig : compiledViewDefinition.getCompiledCalculationConfigurations()) {
      String calcConfigName = compiledCalcConfig.getName();

      for (ValueSpecification valueSpec : compiledCalcConfig.getTerminalOutputSpecifications().keySet()) {
        if (!targetTypes.contains(valueSpec.getTargetSpecification().getType())) {
          // Not relevant
          continue;
        }

        if (impliedTargets != null) {
          impliedTargets.add(valueSpec.getTargetSpecification());
        }

        String valueName = valueSpec.getValueName();
        ValueProperties valueProperties = valueSpec.getProperties();
        RequirementBasedColumnKey specificationBasedKey = new RequirementBasedColumnKey(calcConfigName, valueName, valueProperties);

        Set<ComputationTargetSpecification> targetsForSpec = specToTargets.get(specificationBasedKey);
        if (targetsForSpec == null) {
          targetsForSpec = new HashSet<ComputationTargetSpecification>();
          specToTargets.put(specificationBasedKey, targetsForSpec);
        }
        targetsForSpec.add(valueSpec.getTargetSpecification());

        if (specToRequirements.containsKey(specificationBasedKey)) {
          // Seen this specification before for a different target, so it has been / will be dealt with
          continue;
        }

        Set<RequirementBasedColumnKey> requirementsSatisfiedBySpec = findRequirementsSatisfiedBySpec(requirementsByConfigValueName, calcConfigName, valueSpec);
        if (requirementsSatisfiedBySpec.isEmpty()) {
          s_logger.warn("Could not find any original requirements satisfied by terminal value specification {}. Assuming this is an unwanted output, so ignoring.", valueSpec);
          continue;
        } else {
          specToRequirements.put(specificationBasedKey, requirementsSatisfiedBySpec);
        }
      }
    }

    if (targets == null) {
      targets = new ArrayList<ComputationTargetSpecification>(impliedTargets);
    }

    Map<ComputationTargetSpecification, Set<RequirementBasedColumnKey>> missingCellMap = generateCompleteMissingCellMap(targets, requirements);
    for (Map.Entry<RequirementBasedColumnKey, Set<ComputationTargetSpecification>> specToTargetsEntry : specToTargets.entrySet()) {
      RequirementBasedColumnKey spec = specToTargetsEntry.getKey();
      Collection<RequirementBasedColumnKey> requirementsForSpec = specToRequirements.get(spec);
      if (requirementsForSpec == null) {
        // No columns identified for spec
        continue;
      }
      Set<ComputationTargetSpecification> targetsForSpec = specToTargetsEntry.getValue();
      for (ComputationTargetSpecification targetForSpec : targetsForSpec) {
        Set<RequirementBasedColumnKey> requirementsForTarget = missingCellMap.get(targetForSpec);
        if (requirementsForTarget == null) {
          // Target not in grid
          continue;
        }
        requirementsForTarget.removeAll(requirementsForSpec);
      }
    }

    return new ValueSpecificationAnalysisResult(specToRequirements, targets, missingCellMap);
  }

  public boolean isEmpty() {
    return _specificationBasedColumns.isEmpty() || _targetIdMap.isEmpty();
  }

  public Collection<WebViewGridColumn> getColumns(String calcConfigName, ValueSpecification valueSpec) {
    return _specificationBasedColumns.get(new RequirementBasedColumnKey(calcConfigName, valueSpec.getValueName(), valueSpec.getProperties()));
  }

  public List<WebViewGridColumn> getColumns() {
    return Collections.unmodifiableList(_orderedColumns);
  }

  public Pair<String, ValueSpecification> findCellSpecification(WebGridCell cell, CompiledViewDefinition compiledViewDefinition) {
    // REVIEW jonathan 2011-11-24 -- this is horrible, but so is the fact that the result mapping logic in this class
    // is needed at all on the client side. Really need to solve [PLAT-1299].
    ComputationTargetSpecification targetSpec = findRow(cell.getRowId());
    if (targetSpec == null) {
      return null;
    }
    // The existing data structures are intended for locating a cell given a value specification. We want to answer the
    // reverse question: what is the value specification for a cell. Without storing this information for every cell in
    // the grid during initialisation, we can use the existing data structures to form candidate specifications, only
    // one of which will be in the dep graph for the target.
    String calcConfigName = null;
    Set<ValueSpecification> specificationCandidates = new HashSet<ValueSpecification>();
    for (Map.Entry<RequirementBasedColumnKey, Collection<WebViewGridColumn>> specificationBasedColumnEntry : _specificationBasedColumns.entrySet()) {
      for (WebViewGridColumn column : specificationBasedColumnEntry.getValue()) {
        if (column.getId() == cell.getColumnId()) {
          RequirementBasedColumnKey specificationBasedColumnKey = specificationBasedColumnEntry.getKey();
          if (calcConfigName == null) {
            calcConfigName = specificationBasedColumnKey.getCalcConfigName();
          } else if (!calcConfigName.equals(specificationBasedColumnKey.getCalcConfigName())) {
            // Shouldn't happen
            throw new OpenGammaRuntimeException("Found multiple calculation configuration names for column ID " + cell.getColumnId());
          }
          ValueSpecification valueSpec = new ValueSpecification(specificationBasedColumnKey.getValueName(), targetSpec, specificationBasedColumnKey.getValueProperties());
          specificationCandidates.add(valueSpec);
        }
      }
    }
    if (calcConfigName == null) {
      return null;
    }
    CompiledViewCalculationConfiguration compiledCalcConfig = compiledViewDefinition.getCompiledCalculationConfiguration(calcConfigName);
    for (ValueSpecification candidateSpec : specificationCandidates) {
      if (compiledCalcConfig.getTerminalOutputSpecifications().keySet().contains(candidateSpec)) {
        return Pair.of(calcConfigName, candidateSpec);
      }
    }
    return null;
  }

  public ComputationTargetSpecification[] getTargets() {
    return _targets;
  }

  public int[] getRowIds(ComputationTargetSpecification target) {
    return _targetIdMap.get(target);
  }

  public ComputationTargetSpecification findRow(int rowId) {
    if ((rowId < 0) || (rowId >= _targets.length)) {
      return null;
    } else {
      return _targets[rowId];
    }
  }

  /**
   * Returns the column numbers of the cells which are unsatisfied in the dependency graph.
   * 
   * @param rowId The zero based row index
   * @return The column indices of cells on the specified row which are unsatisfied in the dependency graph
   */
  public Set<Integer> getUnsatisfiedCells(int rowId) {
    return _unsatisfiedCells.get(rowId);
  }

  //-------------------------------------------------------------------------
  private static String getColumnHeader(RequirementBasedColumnKey requirementBasedKey) {
    String normalizedCalcConfigName = requirementBasedKey.getCalcConfigName().toLowerCase().trim();
    if ("default".equals(normalizedCalcConfigName) || "portfolio".equals(normalizedCalcConfigName)) {
      return requirementBasedKey.getValueName();
    }
    return requirementBasedKey.getCalcConfigName() + "/" + requirementBasedKey.getValueName();
  }

  private static String getColumnDescription(RequirementBasedColumnKey requirementBasedKey) {
    return getPropertiesString(requirementBasedKey.getValueProperties());
  }

  private static String getPropertiesString(ValueProperties constraints) {
    if (constraints.isEmpty()) {
      return "No constraints";
    }

    StringBuilder sb = new StringBuilder();
    boolean firstProperty = true;
    for (String propertyName : constraints.getProperties()) {
      if (ValuePropertyNames.FUNCTION.equals(propertyName)) {
        continue;
      }
      if (firstProperty) {
        firstProperty = false;
      } else {
        sb.append("; \n");
      }
      sb.append(propertyName).append("=");
      Set<String> propertyValues = constraints.getValues(propertyName);
      boolean isOptional = constraints.isOptional(propertyName);
      if (propertyValues.size() == 0) {
        sb.append("[empty]");
      } else if (propertyValues.size() == 1 && !isOptional) {
        sb.append(propertyValues.iterator().next());
      } else {
        sb.append("(");
        boolean firstValue = true;
        for (String propertyValue : propertyValues) {
          if (firstValue) {
            firstValue = false;
          } else {
            sb.append(", ");
          }
          sb.append(propertyValue);
        }
        sb.append(")");
      }
      if (isOptional) {
        sb.append("?");
      }
    }
    return sb.toString();
  }

  private static Map<Pair<String, String>, Set<RequirementBasedColumnKey>> getRequirementsMap(Collection<RequirementBasedColumnKey> requirements) {
    Map<Pair<String, String>, Set<RequirementBasedColumnKey>> result = new HashMap<Pair<String, String>, Set<RequirementBasedColumnKey>>();
    for (RequirementBasedColumnKey requirement : requirements) {
      Pair<String, String> requirementKey = Pair.of(requirement.getCalcConfigName(), requirement.getValueName());
      Set<RequirementBasedColumnKey> requirementsSet = result.get(requirementKey);
      if (requirementsSet == null) {
        requirementsSet = new HashSet<RequirementBasedColumnKey>();
        result.put(requirementKey, requirementsSet);
      }
      requirementsSet.add(requirement);
    }
    return result;
  }

  private static Map<ComputationTargetSpecification, Set<RequirementBasedColumnKey>> generateCompleteMissingCellMap(
      Collection<ComputationTargetSpecification> targets, Collection<RequirementBasedColumnKey> requirements) {
    Map<ComputationTargetSpecification, Set<RequirementBasedColumnKey>> result = new HashMap<ComputationTargetSpecification, Set<RequirementBasedColumnKey>>();
    for (ComputationTargetSpecification target : targets) {
      result.put(target, new HashSet<RequirementBasedColumnKey>(requirements));
    }
    return result;
  }

  private static Set<RequirementBasedColumnKey> findRequirementsSatisfiedBySpec(Map<Pair<String, String>,
      Set<RequirementBasedColumnKey>> requirementsMap, String calcConfigName, ValueSpecification valueSpec) {
    Set<RequirementBasedColumnKey> requirementsSet = requirementsMap.get(Pair.of(calcConfigName, valueSpec.getValueName()));
    if (requirementsSet == null) {
      return Collections.emptySet();
    }
    Set<RequirementBasedColumnKey> matches = new HashSet<RequirementBasedColumnKey>();
    for (RequirementBasedColumnKey key : requirementsSet) {
      if (key.getValueProperties().isSatisfiedBy(valueSpec.getProperties())) {
        matches.add(key);
      }
    }
    return matches;
  }

  private static class ValueSpecificationAnalysisResult {

    private final Map<RequirementBasedColumnKey, Collection<RequirementBasedColumnKey>> _specificationToRequirements;
    private final List<ComputationTargetSpecification> _targets;
    private final Map<ComputationTargetSpecification, Set<RequirementBasedColumnKey>> _unsatisfiedRequirementMap;

    public ValueSpecificationAnalysisResult(Map<RequirementBasedColumnKey,
        Collection<RequirementBasedColumnKey>> specificationToRequirements, List<ComputationTargetSpecification> targets,
        Map<ComputationTargetSpecification, Set<RequirementBasedColumnKey>> unsatisfiedRequirementMap) {
      _specificationToRequirements = specificationToRequirements;
      _targets = targets;
      _unsatisfiedRequirementMap = unsatisfiedRequirementMap;
    }

    public Map<RequirementBasedColumnKey, Collection<RequirementBasedColumnKey>> getSpecificationToRequirements() {
      return _specificationToRequirements;
    }

    public List<ComputationTargetSpecification> getTargets() {
      return _targets;
    }

    public Set<RequirementBasedColumnKey> getUnsatisfiedRequirements(ComputationTargetSpecification target) {
      return _unsatisfiedRequirementMap.get(target);
    }

  }

}
