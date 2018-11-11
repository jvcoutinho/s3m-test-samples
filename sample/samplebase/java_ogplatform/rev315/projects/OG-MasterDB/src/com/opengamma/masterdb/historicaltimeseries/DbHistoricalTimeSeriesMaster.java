/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.masterdb.historicaltimeseries;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.time.Duration;
import javax.time.Instant;
import javax.time.calendar.LocalDate;
import javax.time.calendar.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import com.opengamma.DataNotFoundException;
import com.opengamma.id.Identifier;
import com.opengamma.id.IdentifierBundleWithDates;
import com.opengamma.id.IdentifierSearch;
import com.opengamma.id.IdentifierWithDates;
import com.opengamma.id.ObjectIdentifiable;
import com.opengamma.id.ObjectIdentifier;
import com.opengamma.id.UniqueIdentifier;
import com.opengamma.id.VersionCorrection;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesInfoDocument;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesInfoHistoryRequest;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesInfoHistoryResult;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesInfoMetaDataRequest;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesInfoMetaDataResult;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesInfoSearchRequest;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesInfoSearchResult;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesMaster;
import com.opengamma.master.historicaltimeseries.ManageableHistoricalTimeSeries;
import com.opengamma.master.historicaltimeseries.ManageableHistoricalTimeSeriesInfo;
import com.opengamma.master.listener.MasterChangedType;
import com.opengamma.masterdb.AbstractDocumentDbMaster;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.db.DbDateUtils;
import com.opengamma.util.db.DbMapSqlParameterSource;
import com.opengamma.util.db.DbSource;
import com.opengamma.util.db.Paging;
import com.opengamma.util.timeseries.localdate.ArrayLocalDateDoubleTimeSeries;
import com.opengamma.util.timeseries.localdate.LocalDateDoubleTimeSeries;

/**
 * A time-series master implementation using a database for persistence.
 * <p>
 * This is a full implementation of the time-series master using an SQL database.
 * Full details of the API are in {@link HistoricalTimeSeriesMaster}.
 * <p>
 * This implementation uses two linked unique identifiers, one for the document
 * and one for the time-series. They share the same scheme, but have different values
 * and versions. All the methods accept both formats although where possible they
 * should be treated separately. 
 * .
 * <p>
 * This class uses SQL via JDBC. The SQL may be changed by subclassing the relevant methods.
 * <p>
 * This class is mutable but must be treated as immutable after configuration.
 */
public class DbHistoricalTimeSeriesMaster extends AbstractDocumentDbMaster<HistoricalTimeSeriesInfoDocument> implements HistoricalTimeSeriesMaster {

  /** Logger. */
  private static final Logger s_logger = LoggerFactory.getLogger(DbHistoricalTimeSeriesMaster.class);

  /**
   * The scheme used for UniqueIdentifier objects.
   */
  public static final String IDENTIFIER_SCHEME_DEFAULT = "DbHts";
  /**
   * The prefix used for data point unique identifiers.
   */
  protected static final String DATA_POINT_PREFIX = "DP";

  /**
   * SQL select.
   */
  protected static final String SELECT =
      "SELECT " +
        "main.id AS doc_id, " +
        "main.oid AS doc_oid, " +
        "main.ver_from_instant AS ver_from_instant, " +
        "main.ver_to_instant AS ver_to_instant, " +
        "main.corr_from_instant AS corr_from_instant, " +
        "main.corr_to_instant AS corr_to_instant, " +
        "nm.name AS name, " +
        "df.name AS data_field, " +
        "ds.name AS data_source, " +
        "dp.name AS data_provider, " +
        "ot.name AS observation_time, " +
        "i.key_scheme AS key_scheme, " +
        "i.key_value AS key_value, " +
        "di.valid_from AS key_valid_from, " +
        "di.valid_to AS key_valid_to ";
  /**
   * SQL from.
   */
  protected static final String FROM =
      "FROM hts_document main " +
        "INNER JOIN hts_name nm ON main.name_id = nm.id  " +
        "INNER JOIN hts_data_field df ON main.data_field_id = df.id  " +
        "INNER JOIN hts_data_source ds ON main.data_source_id = ds.id  " +
        "INNER JOIN hts_data_provider dp ON main.data_provider_id = dp.id  " +
        "INNER JOIN hts_observation_time ot ON main.observation_time_id = ot.id  " +
        "LEFT JOIN hts_doc2idkey di ON (di.doc_id = main.id) " +
        "LEFT JOIN hts_idkey i ON (di.idkey_id = i.id) ";

  /**
   * Dimension table.
   */
  private final NamedDimensionDbTable _nameTable;
  /**
   * Dimension table.
   */
  private final NamedDimensionDbTable _dataFieldTable;
  /**
   * Dimension table.
   */
  private final NamedDimensionDbTable _dataSourceTable;
  /**
   * Dimension table.
   */
  private final NamedDimensionDbTable _dataProviderTable;
  /**
   * Dimension table.
   */
  private final NamedDimensionDbTable _observationTimeTable;

  /**
   * Creates an instance.
   * 
   * @param dbSource  the database source combining all configuration, not null
   */
  public DbHistoricalTimeSeriesMaster(final DbSource dbSource) {
    super(dbSource, IDENTIFIER_SCHEME_DEFAULT);
    _nameTable = new NamedDimensionDbTable(dbSource, "name", "hts_name", "hts_dimension_seq");
    _dataFieldTable = new NamedDimensionDbTable(dbSource, "data_field", "hts_data_field", "hts_dimension_seq");
    _dataSourceTable = new NamedDimensionDbTable(dbSource, "data_source", "hts_data_source", "hts_dimension_seq");
    _dataProviderTable = new NamedDimensionDbTable(dbSource, "data_provider", "hts_data_provider", "hts_dimension_seq");
    _observationTimeTable = new NamedDimensionDbTable(dbSource, "observation_time", "hts_observation_time", "hts_dimension_seq");
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the dimension table helper.
   * 
   * @return the table, not null
   */
  protected NamedDimensionDbTable getNameTable() {
    return _nameTable;
  }

  /**
   * Gets the dimension table helper.
   * 
   * @return the table, not null
   */
  protected NamedDimensionDbTable getDataFieldTable() {
    return _dataFieldTable;
  }

  /**
   * Gets the dimension table helper.
   * 
   * @return the table, not null
   */
  protected NamedDimensionDbTable getDataSourceTable() {
    return _dataSourceTable;
  }

  /**
   * Gets the dimension table helper.
   * 
   * @return the table, not null
   */
  protected NamedDimensionDbTable getDataProviderTable() {
    return _dataProviderTable;
  }

  /**
   * Gets the dimension table helper.
   * 
   * @return the table, not null
   */
  protected NamedDimensionDbTable getObservationTimeTable() {
    return _observationTimeTable;
  }

  //-------------------------------------------------------------------------
  @Override
  public HistoricalTimeSeriesInfoMetaDataResult metaData(HistoricalTimeSeriesInfoMetaDataRequest request) {
    ArgumentChecker.notNull(request, "request");
    HistoricalTimeSeriesInfoMetaDataResult result = new HistoricalTimeSeriesInfoMetaDataResult();
    if (request.isDataFields()) {
      result.setDataFields(getDataFieldTable().names());
    }
    if (request.isDataSources()) {
      result.setDataSources(getDataSourceTable().names());
    }
    if (request.isDataProviders()) {
      result.setDataProviders(getDataProviderTable().names());
    }
    if (request.isObservationTimes()) {
      result.setObservationTimes(getObservationTimeTable().names());
    }
    return result;
  }

  //-------------------------------------------------------------------------
  @Override
  public HistoricalTimeSeriesInfoSearchResult search(final HistoricalTimeSeriesInfoSearchRequest request) {
    ArgumentChecker.notNull(request, "request");
    ArgumentChecker.notNull(request.getPagingRequest(), "request.pagingRequest");
    ArgumentChecker.notNull(request.getVersionCorrection(), "request.versionCorrection");
    s_logger.debug("search {}", request);
    
    final HistoricalTimeSeriesInfoSearchResult result = new HistoricalTimeSeriesInfoSearchResult();
    if ((request.getInfoIds() != null && request.getInfoIds().size() == 0) ||
        (IdentifierSearch.canMatch(request.getIdentifierKeys()) == false)) {
      result.setPaging(Paging.of(request.getPagingRequest(), 0));
      return result;
    }
    final VersionCorrection vc = request.getVersionCorrection().withLatestFixed(now());
    final DbMapSqlParameterSource args = new DbMapSqlParameterSource()
      .addTimestamp("version_as_of_instant", vc.getVersionAsOf())
      .addTimestamp("corrected_to_instant", vc.getCorrectedTo())
      .addValueNullIgnored("name", getDbHelper().sqlWildcardAdjustValue(request.getName()))
      .addValueNullIgnored("data_field", getDbHelper().sqlWildcardAdjustValue(request.getDataField()))
      .addValueNullIgnored("data_source", getDbHelper().sqlWildcardAdjustValue(request.getDataSource()))
      .addValueNullIgnored("data_provider", getDbHelper().sqlWildcardAdjustValue(request.getDataProvider()))
      .addValueNullIgnored("observation_time", getDbHelper().sqlWildcardAdjustValue(request.getObservationTime()))
      .addDateNullIgnored("id_validity_date", request.getIdentifierValidityDate())
      .addValueNullIgnored("key_value", getDbHelper().sqlWildcardAdjustValue(request.getIdentifierValue()));
    if (request.getIdentifierKeys() != null) {
      int i = 0;
      for (Identifier id : request.getIdentifierKeys()) {
        args.addValue("key_scheme" + i, id.getScheme().getName());
        args.addValue("key_value" + i, id.getValue());
        i++;
      }
    }
    searchWithPaging(request.getPagingRequest(), sqlSearchHistoricalTimeSeries(request), args, new HistoricalTimeSeriesDocumentExtractor(), result);
    return result;
  }

  /**
   * Gets the SQL to search for documents.
   * 
   * @param request  the request, not null
   * @return the SQL search and count, not null
   */
  protected String[] sqlSearchHistoricalTimeSeries(final HistoricalTimeSeriesInfoSearchRequest request) {
    String where = "WHERE ver_from_instant <= :version_as_of_instant AND ver_to_instant > :version_as_of_instant " +
                "AND corr_from_instant <= :corrected_to_instant AND corr_to_instant > :corrected_to_instant ";
    if (request.getName() != null) {
      where += "AND name_id IN (" + getNameTable().sqlSelectSearch(request.getName()) + ") ";
    }
    if (request.getDataField() != null) {
      where += "AND data_field_id IN (" + getDataFieldTable().sqlSelectSearch(request.getDataField()) + ") ";
    }
    if (request.getDataSource() != null) {
      where += "AND data_source_id IN (" + getDataSourceTable().sqlSelectSearch(request.getDataSource()) + ") ";
    }
    if (request.getDataProvider() != null) {
      where += "AND data_provider_id IN (" + getDataProviderTable().sqlSelectSearch(request.getDataProvider()) + ") ";
    }
    if (request.getObservationTime() != null) {
      where += "AND observation_time_id IN (" + getObservationTimeTable().sqlSelectSearch(request.getObservationTime()) + ") ";
    }
    if (request.getInfoIds() != null) {
      StringBuilder buf = new StringBuilder(request.getInfoIds().size() * 10);
      for (ObjectIdentifier objectId : request.getInfoIds()) {
        checkScheme(objectId);
        buf.append(extractOid(objectId)).append(", ");
      }
      buf.setLength(buf.length() - 2);
      where += "AND oid IN (" + buf + ") ";
    }
    if (request.getIdentifierKeys() != null && request.getIdentifierKeys().size() > 0) {
      where += sqlSelectMatchingHistoricalTimeSeriesKeys(request.getIdentifierKeys(), request.getIdentifierValidityDate());
    }
    if (request.getIdentifierValue() != null) {
      where += sqlSelectIdentifierValue(request.getIdentifierValue(), request.getIdentifierValidityDate());
    }
    where += sqlAdditionalWhere();
    
    String selectFromWhereInner = "SELECT id FROM hts_document " + where;
    String inner = getDbHelper().sqlApplyPaging(selectFromWhereInner, "ORDER BY id ", request.getPagingRequest());
    String search = sqlSelectFrom() + "WHERE main.id IN (" + inner + ") ORDER BY main.id" + sqlAdditionalOrderBy(false);
    String count = "SELECT COUNT(*) FROM hts_document " + where;
    return new String[] {search, count};
  }

  /**
   * Gets the SQL to match identifier value.
   * 
   * @param identifierValue the identifier value, not null
   * @param validityDate  the validity date, not null
   * @return the SQL, not null
   */
  protected String sqlSelectIdentifierValue(final String identifierValue, final LocalDate validityDate) {
    String select = "SELECT DISTINCT doc_id " +
        "FROM hts_doc2idkey di, hts_document main " +
        "WHERE doc_id = main.id " +
        "AND main.ver_from_instant <= :version_as_of_instant AND main.ver_to_instant > :version_as_of_instant " +
        "AND main.corr_from_instant <= :corrected_to_instant AND main.corr_to_instant > :corrected_to_instant " +
        (validityDate != null ? "AND di.valid_from <= :id_validity_date AND di.valid_to >= :id_validity_date " : "") +
        "AND idkey_id IN ( SELECT id FROM hts_idkey WHERE " + getDbHelper().sqlWildcardQuery("UPPER(key_value) ", "UPPER(:key_value)", identifierValue) + ") ";
    return "AND id IN (" + select + ") ";
  }

  /**
   * Gets the SQL to match the {@code IdentifierSearch}.
   * 
   * @param idSearch  the identifier search, not null
   * @param validityDate  the validity date, not null
   * @return the SQL, not null
   */
  protected String sqlSelectMatchingHistoricalTimeSeriesKeys(final IdentifierSearch idSearch, final LocalDate validityDate) {
    switch (idSearch.getSearchType()) {
      case EXACT:
        return "AND id IN (" + sqlSelectMatchingHistoricalTimeSeriesKeysExact(idSearch, validityDate) + ") ";
      case ALL:
        return "AND id IN (" + sqlSelectMatchingHistoricalTimeSeriesKeysAll(idSearch, validityDate) + ") ";
      case ANY:
        return "AND id IN (" + sqlSelectMatchingHistoricalTimeSeriesKeysAny(idSearch, validityDate) + ") ";
      case NONE:
        return "AND id NOT IN (" + sqlSelectMatchingHistoricalTimeSeriesKeysAny(idSearch, validityDate) + ") ";
    }
    throw new UnsupportedOperationException("Search type is not supported: " + idSearch.getSearchType());
  }

  /**
   * Gets the SQL to find all the series matching.
   * 
   * @param idSearch  the identifier search, not null
   * @param validityDate  the validity date, not null
   * @return the SQL, not null
   */
  protected String sqlSelectMatchingHistoricalTimeSeriesKeysExact(final IdentifierSearch idSearch, final LocalDate validityDate) {
    // compare size of all matched to size in total
    // filter by dates to reduce search set
    String a = "SELECT doc_id AS matched_doc_id, COUNT(doc_id) AS matched_count " +
      "FROM hts_doc2idkey di, hts_document main " +
      "WHERE di.doc_id = main.id " +
      "AND main.ver_from_instant <= :version_as_of_instant AND main.ver_to_instant > :version_as_of_instant " +
      "AND main.corr_from_instant <= :corrected_to_instant AND main.corr_to_instant > :corrected_to_instant " +
      (validityDate != null ? "AND di.valid_from <= :id_validity_date AND di.valid_to >= :id_validity_date " : "") +
      "AND idkey_id IN (" + sqlSelectMatchingHistoricalTimeSeriesKeysOr(idSearch) + ") " +
      "GROUP BY doc_id " +
      "HAVING COUNT(doc_id) >= " + idSearch.size() + " ";
    String b = "SELECT doc_id AS total_doc_id, COUNT(doc_id) AS total_count " +
      "FROM hts_doc2idkey di, hts_document main " +
      "WHERE di.doc_id = main.id " +
      "AND main.ver_from_instant <= :version_as_of_instant AND main.ver_to_instant > :version_as_of_instant " +
      "AND main.corr_from_instant <= :corrected_to_instant AND main.corr_to_instant > :corrected_to_instant " +
      (validityDate != null ? "AND di.valid_from <= :id_validity_date AND di.valid_to >= :id_validity_date " : "") +
      "GROUP BY doc_id ";
    String select = "SELECT matched_doc_id AS doc_id " +
      "FROM (" + a + ") AS a, (" + b + ") AS b " +
      "WHERE matched_doc_id = total_doc_id " +
        "AND matched_count = total_count ";
    return select;
  }

  /**
   * Gets the SQL to find all the series matching.
   * 
   * @param idSearch  the identifier search, not null
   * @param validityDate  the validity date, not null
   * @return the SQL, not null
   */
  protected String sqlSelectMatchingHistoricalTimeSeriesKeysAll(final IdentifierSearch idSearch, final LocalDate validityDate) {
    // only return doc_id when all requested ids match (having count >= size)
    // filter by dates to reduce search set
    String select = "SELECT doc_id " +
      "FROM hts_doc2idkey di, hts_document main " +
      "WHERE di.doc_id = main.id " +
      "AND main.ver_from_instant <= :version_as_of_instant AND main.ver_to_instant > :version_as_of_instant " +
      "AND main.corr_from_instant <= :corrected_to_instant AND main.corr_to_instant > :corrected_to_instant " +
      (validityDate != null ? "AND di.valid_from <= :id_validity_date AND di.valid_to >= :id_validity_date " : "") +
      "AND idkey_id IN (" + sqlSelectMatchingHistoricalTimeSeriesKeysOr(idSearch) + ") " +
      "GROUP BY doc_id " +
      "HAVING COUNT(doc_id) >= " + idSearch.size() + " ";
    return select;
  }

  /**
   * Gets the SQL to find all the series matching any identifier.
   * 
   * @param idSearch  the identifier search, not null
   * @param validityDate  the validity date, not null
   * @return the SQL, not null
   */
  protected String sqlSelectMatchingHistoricalTimeSeriesKeysAny(final IdentifierSearch idSearch, final LocalDate validityDate) {
    // optimized search for commons case of individual ORs
    // filter by dates to reduce search set
    String select = "SELECT DISTINCT doc_id " +
      "FROM hts_doc2idkey di, hts_document main " +
      "WHERE di.doc_id = main.id " +
      "AND main.ver_from_instant <= :version_as_of_instant AND main.ver_to_instant > :version_as_of_instant " +
      "AND main.corr_from_instant <= :corrected_to_instant AND main.corr_to_instant > :corrected_to_instant " +
      (validityDate != null ? "AND di.valid_from <= :id_validity_date AND di.valid_to >= :id_validity_date " : "") +
      "AND idkey_id IN (" + sqlSelectMatchingHistoricalTimeSeriesKeysOr(idSearch) + ") ";
    return select;
  }

  /**
   * Gets the SQL to find all the ids for a single bundle.
   * 
   * @param idSearch  the identifier search, not null
   * @return the SQL, not null
   */
  protected String sqlSelectMatchingHistoricalTimeSeriesKeysOr(final IdentifierSearch idSearch) {
    String select = "SELECT id FROM hts_idkey ";
    for (int i = 0; i < idSearch.size(); i++) {
      select += (i == 0 ? "WHERE " : "OR ");
      select += "(key_scheme = :key_scheme" + i + " AND key_value = :key_value" + i + ") ";
    }
    return select;
  }

  //-------------------------------------------------------------------------
  @Override
  public HistoricalTimeSeriesInfoDocument get(UniqueIdentifier uniqueId) {
    ArgumentChecker.notNull(uniqueId, "uniqueId");
    if (uniqueId.getVersion() != null && uniqueId.getVersion().contains("P")) {
      VersionCorrection vc = extractTimeSeriesInstants(uniqueId);
      return get(uniqueId.getObjectId(), vc);
    }
    return doGet(uniqueId, new HistoricalTimeSeriesDocumentExtractor(), "HistoricalTimeSeries");
  }

  //-------------------------------------------------------------------------
  @Override
  public HistoricalTimeSeriesInfoDocument get(final ObjectIdentifiable objectId, final VersionCorrection versionCorrection) {
    return doGetByOidInstants(objectId, versionCorrection, new HistoricalTimeSeriesDocumentExtractor(), "HistoricalTimeSeries");
  }

  //-------------------------------------------------------------------------
  @Override
  public HistoricalTimeSeriesInfoHistoryResult history(final HistoricalTimeSeriesInfoHistoryRequest request) {
    return doHistory(request, new HistoricalTimeSeriesInfoHistoryResult(), new HistoricalTimeSeriesDocumentExtractor());
  }

  //-------------------------------------------------------------------------
  /**
   * Inserts a new document.
   * 
   * @param document  the document, not null
   * @return the new document, not null
   */
  @Override
  protected HistoricalTimeSeriesInfoDocument insert(final HistoricalTimeSeriesInfoDocument document) {
    ArgumentChecker.notNull(document.getInfo(), "document.info");
    ArgumentChecker.notNull(document.getInfo().getName(), "document.info.name");
    ArgumentChecker.notNull(document.getInfo().getDataField(), "document.info.dataField");
    ArgumentChecker.notNull(document.getInfo().getDataSource(), "document.info.dataSource");
    ArgumentChecker.notNull(document.getInfo().getDataProvider(), "document.info.dataProvider");
    ArgumentChecker.notNull(document.getInfo().getObservationTime(), "document.info.observationTime");
    
    final long docId = nextId("hts_master_seq");
    final long docOid = (document.getUniqueId() != null ? extractOid(document.getUniqueId()) : docId);
    // the arguments for inserting into the table
    final ManageableHistoricalTimeSeriesInfo info = document.getInfo();
    final DbMapSqlParameterSource seriesArgs = new DbMapSqlParameterSource()
      .addValue("doc_id", docId)
      .addValue("doc_oid", docOid)
      .addTimestamp("ver_from_instant", document.getVersionFromInstant())
      .addTimestampNullFuture("ver_to_instant", document.getVersionToInstant())
      .addTimestamp("corr_from_instant", document.getCorrectionFromInstant())
      .addTimestampNullFuture("corr_to_instant", document.getCorrectionToInstant())
      .addValue("name_id", getNameTable().ensure(info.getName()))
      .addValue("data_field_id", getDataFieldTable().ensure(info.getDataField()))
      .addValue("data_source_id", getDataSourceTable().ensure(info.getDataSource()))
      .addValue("data_provider_id", getDataProviderTable().ensure(info.getDataProvider()))
      .addValue("observation_time_id", getObservationTimeTable().ensure(info.getObservationTime()));
    // the arguments for inserting into the idkey tables
    final List<DbMapSqlParameterSource> assocList = new ArrayList<DbMapSqlParameterSource>();
    final List<DbMapSqlParameterSource> idKeyList = new ArrayList<DbMapSqlParameterSource>();
    for (IdentifierWithDates id : info.getIdentifiers()) {
      final DbMapSqlParameterSource assocArgs = new DbMapSqlParameterSource()
        .addValue("doc_id", docId)
        .addValue("key_scheme", id.getIdentityKey().getScheme().getName())
        .addValue("key_value", id.getIdentityKey().getValue())
        .addValue("valid_from", DbDateUtils.toSqlDateNullFarPast(id.getValidFrom()))
        .addValue("valid_to", DbDateUtils.toSqlDateNullFarFuture(id.getValidTo()));
      assocList.add(assocArgs);
      if (getJdbcTemplate().queryForList(sqlSelectIdKey(), assocArgs).isEmpty()) {
        // select avoids creating unecessary id, but id may still not be used
        final long idKeyId = nextId("hts_idkey_seq");
        final DbMapSqlParameterSource idkeyArgs = new DbMapSqlParameterSource()
          .addValue("idkey_id", idKeyId)
          .addValue("key_scheme", id.getIdentityKey().getScheme().getName())
          .addValue("key_value", id.getIdentityKey().getValue());
        idKeyList.add(idkeyArgs);
      }
    }
    getJdbcTemplate().update(sqlInsertHistoricalTimeSeries(), seriesArgs);
    getJdbcTemplate().batchUpdate(sqlInsertIdKey(), idKeyList.toArray(new DbMapSqlParameterSource[idKeyList.size()]));
    getJdbcTemplate().batchUpdate(sqlInsertHtsIdKey(), assocList.toArray(new DbMapSqlParameterSource[assocList.size()]));
    // set the uniqueId
    final UniqueIdentifier uniqueId = createUniqueIdentifier(docOid, docId);
    info.setUniqueId(uniqueId);
    document.setUniqueId(uniqueId);
    document.getInfo().setTimeSeriesObjectId(uniqueId.getObjectId().withValue(DATA_POINT_PREFIX + uniqueId.getValue()));
    return document;
  }

  /**
   * Gets the SQL for inserting a document.
   * 
   * @return the SQL, not null
   */
  protected String sqlInsertHistoricalTimeSeries() {
    return "INSERT INTO hts_document " +
              "(id, oid, ver_from_instant, ver_to_instant, corr_from_instant, corr_to_instant, name_id, " +
              "data_field_id, data_source_id, data_provider_id, observation_time_id) " +
            "VALUES " +
              "(:doc_id, :doc_oid, :ver_from_instant, :ver_to_instant, :corr_from_instant, :corr_to_instant, :name_id, " +
              ":data_field_id, :data_source_id, :data_provider_id, :observation_time_id)";
  }

  /**
   * Gets the SQL for inserting an hts-idkey association.
   * 
   * @return the SQL, not null
   */
  protected String sqlInsertHtsIdKey() {
    return "INSERT INTO hts_doc2idkey " +
              "(doc_id, idkey_id, valid_from, valid_to) " +
            "VALUES " +
              "(:doc_id, (" + sqlSelectIdKey() + "), :valid_from, :valid_to)";
  }

  /**
   * Gets the SQL for selecting an idkey.
   * 
   * @return the SQL, not null
   */
  protected String sqlSelectIdKey() {
    return "SELECT id FROM hts_idkey WHERE key_scheme = :key_scheme AND key_value = :key_value";
  }

  /**
   * Gets the SQL for inserting an idkey.
   * 
   * @return the SQL, not null
   */
  protected String sqlInsertIdKey() {
    return "INSERT INTO hts_idkey (id, key_scheme, key_value) " +
            "VALUES (:idkey_id, :key_scheme, :key_value)";
  }

  //-------------------------------------------------------------------------
  @Override
  public ManageableHistoricalTimeSeries getTimeSeries(
      UniqueIdentifier uniqueId, LocalDate fromDateInclusive, LocalDate toDateInclusive) {
    ArgumentChecker.notNull(uniqueId, "uniqueId");
    checkScheme(uniqueId);
    final VersionCorrection vc;
    if (uniqueId.isVersioned() && uniqueId.getValue().startsWith(DATA_POINT_PREFIX)) {
      vc = extractTimeSeriesInstants(uniqueId);
    } else {
      vc = VersionCorrection.LATEST;
    }
    return getTimeSeries(uniqueId, vc, fromDateInclusive, toDateInclusive);
  }

  //-------------------------------------------------------------------------
  @Override
  public ManageableHistoricalTimeSeries getTimeSeries(
      ObjectIdentifiable objectId, VersionCorrection versionCorrection, LocalDate fromDateInclusive, LocalDate toDateInclusive) {
    final long oid = extractOid(objectId);
    final VersionCorrection vc = versionCorrection.withLatestFixed(now());
    final DbMapSqlParameterSource args = new DbMapSqlParameterSource()
      .addValue("doc_oid", oid)
      .addTimestamp("version_as_of_instant", vc.getVersionAsOf())
      .addTimestamp("corrected_to_instant", vc.getCorrectedTo())
      .addValue("start_date", DbDateUtils.toSqlDateNullFarPast(fromDateInclusive))
      .addValue("end_date", DbDateUtils.toSqlDateNullFarFuture(toDateInclusive));
    final NamedParameterJdbcOperations namedJdbc = getDbSource().getJdbcTemplate().getNamedParameterJdbcOperations();
    ManageableHistoricalTimeSeries result = namedJdbc.query(sqlSelectDataPointsCommon(), args, new ManageableHTSExtractor(oid));
    if (result == null) {
      throw new DataNotFoundException("Unable to find time-series: " + objectId);
    }
    LocalDateDoubleTimeSeries series = namedJdbc.query(sqlSelectDataPoints(), args, new DataPointsExtractor());
    result.setTimeSeries(series);
    return result;
  }

  /**
   * Gets the SQL to load the data point common info.
   * 
   * @return the SQL, not null
   */
  protected String sqlSelectDataPointsCommon() {
    // find latest version-correction before query instants and min/max dates
    String selectCommon =
      "SELECT doc_oid, MAX(ver_instant) AS max_ver_instant, MAX(corr_instant) AS max_corr_instant, " +
      "MAX(point_date) AS max_point_date, MIN(point_date) AS min_point_date " +
      "FROM hts_point " +
      "WHERE doc_oid = :doc_oid " +
      "AND ver_instant <= :version_as_of_instant " +
      "AND corr_instant <= :corrected_to_instant " +
      "GROUP BY doc_oid ";
    // select document table to handle empty set of points and to handle removal
    String selectMain = "SELECT main.ver_from_instant AS ver_from_instant, main.corr_from_instant AS corr_from_instant, common.* " +
        "FROM hts_document main " +
        "LEFT JOIN (" + selectCommon + ") common ON main.oid = common.doc_oid " +
        "WHERE main.oid = :doc_oid " +
        "AND main.ver_from_instant <= :version_as_of_instant AND main.ver_to_instant > :version_as_of_instant " +
        "AND main.corr_from_instant <= :corrected_to_instant AND main.corr_to_instant > :corrected_to_instant ";
    return selectMain;
  }

  /**
   * Gets the SQL to load the data points.
   * 
   * @return the SQL, not null
   */
  protected String sqlSelectDataPoints() {
    String selectPoints =
      "SELECT point_date, point_value " +
      "FROM hts_point " +
      "WHERE doc_oid = :doc_oid " +
      "AND ver_instant <= :version_as_of_instant " +
      "AND corr_instant <= :corrected_to_instant " +
      "AND point_date >= :start_date " +
      "AND point_date <= :end_date " +
      "ORDER BY point_date, corr_instant DESC ";
    return selectPoints;
  }

  //-------------------------------------------------------------------------
  @Override
  public UniqueIdentifier updateTimeSeriesDataPoints(final ObjectIdentifiable objectId, final LocalDateDoubleTimeSeries series) {
    ArgumentChecker.notNull(objectId, "objectId");
    ArgumentChecker.notNull(series, "series");
    s_logger.debug("add time-series data points to {}", objectId);
    
    // retry to handle concurrent conflicts
    for (int retry = 0; true; retry++) {
      final UniqueIdentifier uniqueId = resolveObjectId(objectId, VersionCorrection.LATEST);
      if (series.isEmpty()) {
        return uniqueId;
      }
      try {
        final Instant now = now();
        UniqueIdentifier resultId = getTransactionTemplate().execute(new TransactionCallback<UniqueIdentifier>() {
          @Override
          public UniqueIdentifier doInTransaction(final TransactionStatus status) {
            insertDataPointsCheckMaxDate(uniqueId, series);
            return insertDataPoints(uniqueId, series, now);
          }
        });
        changeManager().masterChanged(MasterChangedType.UPDATED, uniqueId, resultId, now);
        return resultId;
      } catch (DataIntegrityViolationException ex) {
        if (retry == getMaxRetries()) {
          throw ex;
        }
      } catch (DataAccessException ex) {
        throw fixSQLExceptionCause(ex);
      }
    }
  }

  /**
   * Checks the data points can be inserted.
   * 
   * @param uniqueId  the unique identifier, not null
   * @param series  the time-series data points, not empty, not null
   */
  protected void insertDataPointsCheckMaxDate(final UniqueIdentifier uniqueId, final LocalDateDoubleTimeSeries series) {
    final Long docOid = extractOid(uniqueId);
    final VersionCorrection vc = extractTimeSeriesInstants(uniqueId);
    final DbMapSqlParameterSource queryArgs = new DbMapSqlParameterSource()
      .addValue("doc_oid", docOid)
      .addTimestamp("ver_instant", vc.getVersionAsOf())
      .addTimestamp("corr_instant", vc.getCorrectedTo());
    Date result = getDbSource().getJdbcTemplate().queryForObject(sqlSelectMaxPointDate(), Date.class, queryArgs);
    if (result != null) {
      LocalDate maxDate = DbDateUtils.fromSqlDateAllowNull(result);
      if (series.getTime(0).isBefore(maxDate)) {
        throw new IllegalArgumentException("Unable to add time-series as it starts before the latest in the database");
      }
    }
  }

  /**
   * Inserts the data points.
   * 
   * @param uniqueId  the unique identifier, not null
   * @param series  the time-series data points, not empty, not null
   * @param now  the current instant, not null
   * @return the unique identifier, not null
   */
  protected UniqueIdentifier insertDataPoints(final UniqueIdentifier uniqueId, final LocalDateDoubleTimeSeries series, final Instant now) {
    final Long docOid = extractOid(uniqueId);
    final Timestamp nowTS = DbDateUtils.toSqlTimestamp(now);
    final List<DbMapSqlParameterSource> argsList = new ArrayList<DbMapSqlParameterSource>();
    for (Entry<LocalDate, Double> entry : series) {
      LocalDate date = entry.getKey();
      Double value = entry.getValue();
      if (date == null || value == null) {
        throw new IllegalArgumentException("Time-series must not contain a null value");
      }
      final DbMapSqlParameterSource args = new DbMapSqlParameterSource()
        .addValue("doc_oid", docOid)
        .addDate("point_date", date)
        .addValue("ver_instant", nowTS)
        .addValue("corr_instant", nowTS)
        .addValue("point_value", value);
      argsList.add(args);
    }
    getJdbcTemplate().batchUpdate(sqlInsertDataPoint(), argsList.toArray(new DbMapSqlParameterSource[argsList.size()]));
    return createTimeSeriesUniqueIdentifier(docOid, now, now);
  }

  /**
   * Gets the SQL for inserting a document.
   * 
   * @return the SQL, not null
   */
  protected String sqlSelectMaxPointDate() {
    return
      "SELECT MAX(point_date) AS max_point_date " +
      "FROM hts_point " +
      "WHERE doc_oid = :doc_oid " +
      "AND ver_instant <= :ver_instant " +
      "AND corr_instant <= :corr_instant ";
  }

  /**
   * Gets the SQL for inserting a document.
   * 
   * @return the SQL, not null
   */
  protected String sqlInsertDataPoint() {
    return
      "INSERT INTO hts_point " +
        "(doc_oid, point_date, ver_instant, corr_instant, point_value) " +
      "VALUES " +
        "(:doc_oid, :point_date, :ver_instant, :corr_instant, :point_value)";
  }

  //-------------------------------------------------------------------------
  @Override
  public UniqueIdentifier correctTimeSeriesDataPoints(final ObjectIdentifiable objectId, final LocalDateDoubleTimeSeries series) {
    ArgumentChecker.notNull(objectId, "objectId");
    ArgumentChecker.notNull(series, "series");
    s_logger.debug("add time-series data points to {}", objectId);
    
    // retry to handle concurrent conflicts
    for (int retry = 0; true; retry++) {
      final UniqueIdentifier uniqueId = resolveObjectId(objectId, VersionCorrection.LATEST);
      if (series.isEmpty()) {
        return uniqueId;
      }
      try {
        final Instant now = now();
        UniqueIdentifier resultId = getTransactionTemplate().execute(new TransactionCallback<UniqueIdentifier>() {
          @Override
          public UniqueIdentifier doInTransaction(final TransactionStatus status) {
            return correctDataPoints(uniqueId, series, now);
          }
        });
        changeManager().masterChanged(MasterChangedType.CORRECTED, uniqueId, resultId, now);
        return resultId;
      } catch (DataIntegrityViolationException ex) {
        if (retry == getMaxRetries()) {
          throw ex;
        }
      } catch (DataAccessException ex) {
        throw fixSQLExceptionCause(ex);
      }
    }
  }

  /**
   * Corrects the data points.
   * 
   * @param uniqueId  the unique identifier, not null
   * @param series  the time-series data points, not empty, not null
   * @param now  the current instant, not null
   * @return the unique identifier, not null
   */
  protected UniqueIdentifier correctDataPoints(UniqueIdentifier uniqueId, LocalDateDoubleTimeSeries series, Instant now) {
    final Long docOid = extractOid(uniqueId);
    final Timestamp nowTS = DbDateUtils.toSqlTimestamp(now);
    final List<DbMapSqlParameterSource> argsList = new ArrayList<DbMapSqlParameterSource>();
    for (Entry<LocalDate, Double> entry : series) {
      LocalDate date = entry.getKey();
      Double value = entry.getValue();
      if (date == null || value == null) {
        throw new IllegalArgumentException("Time-series must not contain a null value");
      }
      final DbMapSqlParameterSource args = new DbMapSqlParameterSource()
        .addValue("doc_oid", docOid)
        .addDate("point_date", date)
        .addValue("corr_instant", nowTS)
        .addValue("point_value", value);
      argsList.add(args);
    }
    getJdbcTemplate().batchUpdate(sqlInsertCorrectDataPoints(), argsList.toArray(new DbMapSqlParameterSource[argsList.size()]));
    return resolveObjectId(uniqueId, VersionCorrection.of(now, now));
  }

  /**
   * Gets the SQL for inserting data points.
   * 
   * @return the SQL, not null
   */
  protected String sqlInsertCorrectDataPoints() {
    return 
      "INSERT INTO hts_point " +
        "(doc_oid, point_date, ver_instant, corr_instant, point_value) " +
      "VALUES " +
        "(:doc_oid, :point_date, " +
          getDbHelper().sqlNullDefault("(SELECT ver_instant FROM hts_point " +
              "WHERE doc_oid = :doc_oid AND point_date = :point_date AND ver_instant = corr_instant)", ":corr_instant") + ", " +
        ":corr_instant, :point_value)";
  }

  //-------------------------------------------------------------------------
  @Override
  public UniqueIdentifier removeTimeSeriesDataPoints(final ObjectIdentifiable objectId, final LocalDate fromDateInclusive, final LocalDate toDateInclusive) {
    ArgumentChecker.notNull(objectId, "objectId");
    if (fromDateInclusive != null && toDateInclusive != null) {
      ArgumentChecker.inOrderOrEqual(fromDateInclusive, toDateInclusive, "fromDateInclusive", "toDateInclusive");
    }
    s_logger.debug("removing time-series data points from {}", objectId);
    
    // retry to handle concurrent conflicts
    for (int retry = 0; true; retry++) {
      final UniqueIdentifier uniqueId = resolveObjectId(objectId, VersionCorrection.LATEST);
      try {
        final Instant now = now();
        UniqueIdentifier resultId = getTransactionTemplate().execute(new TransactionCallback<UniqueIdentifier>() {
          @Override
          public UniqueIdentifier doInTransaction(final TransactionStatus status) {
            return removeDataPoints(uniqueId, fromDateInclusive, toDateInclusive, now);
          }
        });
        changeManager().masterChanged(MasterChangedType.UPDATED, uniqueId, resultId, now);
        return resultId;
      } catch (DataIntegrityViolationException ex) {
        if (retry == getMaxRetries()) {
          throw ex;
        }
      } catch (DataAccessException ex) {
        throw fixSQLExceptionCause(ex);
      }
    }
  }

  /**
   * Removes data points.
   * 
   * @param uniqueId  the unique identifier, not null
   * @param fromDateInclusive  the start date to remove from, not null
   * @param toDateInclusive  the end date to remove to, not null
   * @param now  the current instant, not null
   * @return the unique identifier, not null
   */
  protected UniqueIdentifier removeDataPoints(UniqueIdentifier uniqueId, LocalDate fromDateInclusive, LocalDate toDateInclusive, Instant now) {
    final Long docOid = extractOid(uniqueId);
    // query dates to remove
    final DbMapSqlParameterSource queryArgs = new DbMapSqlParameterSource()
      .addValue("doc_oid", docOid)
      .addValue("start_date", DbDateUtils.toSqlDateNullFarPast(fromDateInclusive))
      .addValue("end_date", DbDateUtils.toSqlDateNullFarFuture(toDateInclusive));
    List<Map<String, Object>> dates = getJdbcTemplate().queryForList(sqlSelectRemoveDataPoints(), queryArgs);
    // insert new rows to remove them
    final Timestamp nowTS = DbDateUtils.toSqlTimestamp(now);
    final List<DbMapSqlParameterSource> argsList = new ArrayList<DbMapSqlParameterSource>();
    for (Map<String, Object> date : dates) {
      final DbMapSqlParameterSource args = new DbMapSqlParameterSource()
        .addValue("doc_oid", docOid)
        .addValue("point_date", date.get("POINT_DATE"))
        .addValue("corr_instant", nowTS);
      args.addValue("point_value", null, Types.DOUBLE);
      argsList.add(args);
    }
    getJdbcTemplate().batchUpdate(sqlInsertCorrectDataPoints(), argsList.toArray(new DbMapSqlParameterSource[argsList.size()]));
    return resolveObjectId(uniqueId, VersionCorrection.of(now, now));
  }

  /**
   * Gets the SQL for selecting data points to be removed.
   * 
   * @return the SQL, not null
   */
  protected String sqlSelectRemoveDataPoints() {
    String select =
      "SELECT DISTINCT point_date " +
      "FROM hts_point " +
      "WHERE doc_oid = :doc_oid " +
      "AND point_date >= :start_date " +
      "AND point_date <= :end_date ";
    return select;
  }

  //-------------------------------------------------------------------------
  /**
   * Creates a unique identifier.
   * 
   * @param oid  the object identifier
   * @param verInstant  the version instant, not null
   * @param corrInstant  the correction instant, not null
   * @return the unique identifier
   */
  protected UniqueIdentifier createTimeSeriesUniqueIdentifier(long oid, Instant verInstant, Instant corrInstant) {
    String oidStr = DATA_POINT_PREFIX + oid;
    Duration dur = Duration.between(verInstant, corrInstant);
    String verStr = verInstant.toString() + dur.toString();
    return UniqueIdentifier.of(getIdentifierScheme(), oidStr, verStr);
  }

  /**
   * Extracts the object row id from the object identifier.
   * 
   * @param objectId  the object identifier, not null
   * @return the date, null if no point date
   */
  @Override
  protected long extractOid(ObjectIdentifiable objectId) {
    String value = objectId.getObjectId().getValue();
    if (value.startsWith(DATA_POINT_PREFIX)) {
      value = value.substring(DATA_POINT_PREFIX.length());
    }
    try {
      return Long.parseLong(value);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("UniqueIdentifier is not from this master (non-numeric object id): " + objectId, ex);
    }
  }

  /**
   * Extracts the instants from the unique identifier.
   * 
   * @param uniqueId  the unique identifier, not null
   * @return the instants, version, correction, not null
   */
  protected VersionCorrection extractTimeSeriesInstants(UniqueIdentifier uniqueId) {
    try {
      int pos = uniqueId.getVersion().indexOf('P');
      String verStr = uniqueId.getVersion().substring(0, pos);
      String corrStr = uniqueId.getVersion().substring(pos);
      Instant ver = OffsetDateTime.parse(verStr).toInstant();
      Instant corr = ver.plus(Duration.parse(corrStr));
      return VersionCorrection.of(ver, corr);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("UniqueIdentifier is not from this master (invalid version): " + uniqueId, ex);
    }
  }

  @Override
  protected long extractRowId(UniqueIdentifier uniqueId) {
    int pos = uniqueId.getVersion().indexOf('P');
    if (pos < 0) {
      return super.extractRowId(uniqueId);
    }
    VersionCorrection vc = extractTimeSeriesInstants(uniqueId);
    HistoricalTimeSeriesInfoDocument doc = get(uniqueId.getObjectId(), vc);  // not very efficient, but works
    return super.extractRowId(doc.getUniqueId());
  }

  //-------------------------------------------------------------------------
  /**
   * Resolves an object identifier to a unique identifier.
   * 
   * @param objectId  the time-series object identifier, not null
   * @param versionCorrection  the version-correction locator to search at, not null
   * @return the time-series, not null
   */
  protected UniqueIdentifier resolveObjectId(ObjectIdentifiable objectId, VersionCorrection versionCorrection) {
    ArgumentChecker.notNull(objectId, "objectId");
    ArgumentChecker.notNull(versionCorrection, "versionCorrection");
    checkScheme(objectId);
    final long oid = extractOid(objectId);
    versionCorrection = versionCorrection.withLatestFixed(now());
    final DbMapSqlParameterSource args = new DbMapSqlParameterSource()
      .addValue("doc_oid", oid)
      .addTimestamp("version_as_of_instant", versionCorrection.getVersionAsOf())
      .addTimestamp("corrected_to_instant", versionCorrection.getCorrectedTo());
    final NamedParameterJdbcOperations namedJdbc = getDbSource().getJdbcTemplate().getNamedParameterJdbcOperations();
    final UniqueIdentifierExtractor extractor = new UniqueIdentifierExtractor(oid);
    UniqueIdentifier uniqueId = namedJdbc.query(sqlSelectUniqueIdentifierByVersionCorrection(), args, extractor);
    if (uniqueId == null) {
      throw new DataNotFoundException("Unable to find time-series: " + objectId.getObjectId());
    }
    return uniqueId;
  }

  /**
   * Gets the SQL to load the data points.
   * 
   * @return the SQL, not null
   */
  protected String sqlSelectUniqueIdentifierByVersionCorrection() {
    // find latest version-correction before query instants
    String selectInstants =
      "SELECT doc_oid, MAX(ver_instant) AS max_ver_instant, MAX(corr_instant) AS max_corr_instant " +
      "FROM hts_point " +
      "WHERE doc_oid = :doc_oid " +
      "AND ver_instant <= :version_as_of_instant " +
      "AND corr_instant <= :corrected_to_instant " +
      "GROUP BY doc_oid ";
    // select document to handle empty series and to check/use first doc instants
    String select =
      "SELECT main.ver_from_instant AS ver_from_instant, main.corr_from_instant AS corr_from_instant, instants.* " +
      "FROM hts_document main " +
      "LEFT JOIN (" + selectInstants + ") instants ON main.oid = instants.doc_oid " +
      "WHERE main.oid = :doc_oid " +
      "AND main.ver_from_instant <= :version_as_of_instant AND main.ver_to_instant > :version_as_of_instant " +
      "AND main.corr_from_instant <= :corrected_to_instant AND main.corr_to_instant > :corrected_to_instant ";
    return select;
  }

  //-------------------------------------------------------------------------
  @Override
  protected String sqlSelectFrom() {
    return SELECT + FROM;
  }

  @Override
  protected String mainTableName() {
    return "hts_document";
  }

  //-------------------------------------------------------------------------
  /**
   * Mapper from SQL rows to a HistoricalTimeSeriesDocument.
   */
  protected final class HistoricalTimeSeriesDocumentExtractor implements ResultSetExtractor<List<HistoricalTimeSeriesInfoDocument>> {
    private long _lastDocId = -1;
    private ManageableHistoricalTimeSeriesInfo _info;
    private List<HistoricalTimeSeriesInfoDocument> _documents = new ArrayList<HistoricalTimeSeriesInfoDocument>();

    @Override
    public List<HistoricalTimeSeriesInfoDocument> extractData(final ResultSet rs) throws SQLException, DataAccessException {
      while (rs.next()) {
        final long docId = rs.getLong("DOC_ID");
        if (_lastDocId != docId) {
          _lastDocId = docId;
          buildHistoricalTimeSeries(rs, docId);
        }
        final String idScheme = rs.getString("KEY_SCHEME");
        final String idValue = rs.getString("KEY_VALUE");
        final LocalDate validFrom = DbDateUtils.fromSqlDateNullFarPast(rs.getDate("KEY_VALID_FROM"));
        final LocalDate validTo = DbDateUtils.fromSqlDateNullFarFuture(rs.getDate("KEY_VALID_TO"));
        if (idScheme != null && idValue != null) {
          IdentifierWithDates id = IdentifierWithDates.of(Identifier.of(idScheme, idValue), validFrom, validTo);
          _info.setIdentifiers(_info.getIdentifiers().withIdentifier(id));
        }
      }
      return _documents;
    }

    private void buildHistoricalTimeSeries(final ResultSet rs, final long docId) throws SQLException {
      final long docOid = rs.getLong("DOC_OID");
      final Timestamp versionFrom = rs.getTimestamp("VER_FROM_INSTANT");
      final Timestamp versionTo = rs.getTimestamp("VER_TO_INSTANT");
      final Timestamp correctionFrom = rs.getTimestamp("CORR_FROM_INSTANT");
      final Timestamp correctionTo = rs.getTimestamp("CORR_TO_INSTANT");
      final String name = rs.getString("NAME");
      final String dataField = rs.getString("DATA_FIELD");
      final String dataSource = rs.getString("DATA_SOURCE");
      final String dataProvider = rs.getString("DATA_PROVIDER");
      final String observationTime = rs.getString("OBSERVATION_TIME");
      
      UniqueIdentifier uniqueId = createUniqueIdentifier(docOid, docId);
      _info = new ManageableHistoricalTimeSeriesInfo();
      _info.setUniqueId(uniqueId);
      _info.setName(name);
      _info.setDataField(dataField);
      _info.setDataSource(dataSource);
      _info.setDataProvider(dataProvider);
      _info.setObservationTime(observationTime);
      _info.setIdentifiers(IdentifierBundleWithDates.EMPTY);
      _info.setTimeSeriesObjectId(uniqueId.getObjectId().withValue(DATA_POINT_PREFIX + uniqueId.getValue()));
      
      HistoricalTimeSeriesInfoDocument doc = new HistoricalTimeSeriesInfoDocument(_info);
      doc.setVersionFromInstant(DbDateUtils.fromSqlTimestamp(versionFrom));
      doc.setVersionToInstant(DbDateUtils.fromSqlTimestampNullFarFuture(versionTo));
      doc.setCorrectionFromInstant(DbDateUtils.fromSqlTimestamp(correctionFrom));
      doc.setCorrectionToInstant(DbDateUtils.fromSqlTimestampNullFarFuture(correctionTo));
      _documents.add(doc);
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Mapper from SQL rows to a LocalDateDoubleTimeSeries.
   */
  protected final class DataPointsExtractor implements ResultSetExtractor<LocalDateDoubleTimeSeries> {
    @Override
    public LocalDateDoubleTimeSeries extractData(final ResultSet rs) throws SQLException, DataAccessException {
      final List<LocalDate> dates = new ArrayList<LocalDate>(256);
      final List<Double> values = new ArrayList<Double>(256);
      LocalDate last = null;
      while (rs.next()) {
        LocalDate date = DbDateUtils.fromSqlDateAllowNull(rs.getDate("POINT_DATE"));
        if (date.equals(last) == false) {
          last = date;
          Double value = (Double) rs.getObject("POINT_VALUE");
          if (value != null) {
            dates.add(date);
            values.add(value);
          }
        }
      }
      return new ArrayLocalDateDoubleTimeSeries(dates, values);
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Mapper from SQL rows to a UniqueIdentifier.
   */
  protected final class UniqueIdentifierExtractor implements ResultSetExtractor<UniqueIdentifier> {
    private final long _objectId;
    public UniqueIdentifierExtractor(final long objectId) {
      _objectId = objectId;
    }
    @Override
    public UniqueIdentifier extractData(final ResultSet rs) throws SQLException, DataAccessException {
      while (rs.next()) {
        Timestamp ver = rs.getTimestamp("max_ver_instant");
        Timestamp corr = rs.getTimestamp("max_corr_instant");
        if (ver == null) {
          ver = rs.getTimestamp("ver_from_instant");
          corr = rs.getTimestamp("corr_from_instant");
        }
        Instant verInstant = DbDateUtils.fromSqlTimestamp(ver);
        Instant corrInstant = (corr != null ? DbDateUtils.fromSqlTimestamp(corr) : verInstant);
        return createTimeSeriesUniqueIdentifier(_objectId, verInstant, corrInstant);
      }
      return null;
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Mapper from SQL rows to a ManageableHistoricalTimeSeries.
   */
  protected final class ManageableHTSExtractor implements ResultSetExtractor<ManageableHistoricalTimeSeries> {
    private final long _objectId;
    public ManageableHTSExtractor(final long objectId) {
      _objectId = objectId;
    }
    @Override
    public ManageableHistoricalTimeSeries extractData(final ResultSet rs) throws SQLException, DataAccessException {
      while (rs.next()) {
        Timestamp ver = rs.getTimestamp("max_ver_instant");
        Timestamp corr = rs.getTimestamp("max_corr_instant");
        if (ver == null) {
          ver = rs.getTimestamp("ver_from_instant");
          corr = rs.getTimestamp("corr_from_instant");
        }
        Instant verInstant = DbDateUtils.fromSqlTimestamp(ver);
        Instant corrInstant = (corr != null ? DbDateUtils.fromSqlTimestamp(corr) : verInstant);
        ManageableHistoricalTimeSeries hts = new ManageableHistoricalTimeSeries();
        hts.setUniqueId(createTimeSeriesUniqueIdentifier(_objectId, verInstant, corrInstant));
        hts.setVersionInstant(verInstant);
        hts.setCorrectionInstant(corrInstant);
        hts.setEarliest(DbDateUtils.fromSqlDateAllowNull(rs.getDate("min_point_date")));
        hts.setLatest(DbDateUtils.fromSqlDateAllowNull(rs.getDate("max_point_date")));
        return hts;
      }
      return null;
    }
  }

}
