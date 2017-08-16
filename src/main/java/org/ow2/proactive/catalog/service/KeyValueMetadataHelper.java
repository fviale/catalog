/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.ow2.proactive.catalog.service;

import java.io.ByteArrayInputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.ow2.proactive.catalog.dto.Metadata;
import org.ow2.proactive.catalog.repository.entity.KeyValueLabelMetadataEntity;
import org.ow2.proactive.catalog.service.exception.UnprocessableEntityException;
import org.ow2.proactive.catalog.service.model.GenericInfoBucketData;
import org.ow2.proactive.catalog.util.parser.CatalogObjectParserFactory;
import org.ow2.proactive.catalog.util.parser.CatalogObjectParserInterface;
import org.ow2.proactive.catalog.util.parser.WorkflowParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @author ActiveEon Team
 * @since 19/06/2017
 */
@Component
public class KeyValueMetadataHelper {

    @SuppressWarnings("FieldCanBeLocal")
    private static final String GROUP_KEY = "group";

    @SuppressWarnings("FieldCanBeLocal")
    private static final String BUCKET_NAME_KEY = "bucketName";

    private final OwnerGroupStringHelper ownerGroupStringHelper;

    @Autowired
    public KeyValueMetadataHelper(OwnerGroupStringHelper ownerGroupStringHelper) {
        this.ownerGroupStringHelper = ownerGroupStringHelper;
    }

    public List<KeyValueLabelMetadataEntity> extractKeyValuesFromRaw(String kind, byte[] rawObject) {
        try {
            CatalogObjectParserInterface catalogObjectParser = CatalogObjectParserFactory.get().getParser(kind);
            return catalogObjectParser.parse(new ByteArrayInputStream(rawObject));
        } catch (XMLStreamException e) {
            throw new UnprocessableEntityException(e);
        }
    }

    public static List<KeyValueLabelMetadataEntity> convertToEntity(List<Metadata> source) {
        return source.stream().map(KeyValueLabelMetadataEntity::new).collect(Collectors.toList());
    }

    public List<Metadata> convertFromEntity(List<KeyValueLabelMetadataEntity> source) {
        return source.stream().map(Metadata::new).collect(Collectors.toList());
    }

    public List<AbstractMap.SimpleImmutableEntry<String, String>>
            toKeyValueEntry(final List<KeyValueLabelMetadataEntity> workflowKeyValueMetadataEntities) {
        return workflowKeyValueMetadataEntities.stream()
                                               .map(this::createSimpleImmutableEntry)
                                               .collect(Collectors.toList());
    }

    public List<KeyValueLabelMetadataEntity> replaceMetadataRelatedGenericInfoAndKeepOthers(
            final List<KeyValueLabelMetadataEntity> workflowMetadataEntities,
            final GenericInfoBucketData genericInfoBucketData) {
        if (workflowMetadataEntities == null) {
            return Collections.emptyList();
        }
        if (genericInfoBucketData == null) {
            return new ArrayList<>(workflowMetadataEntities);
        }

        List<KeyValueLabelMetadataEntity> metadataListWithGroup = replaceOrAddGenericInfoKey(workflowMetadataEntities,
                                                                                             GROUP_KEY,
                                                                                             ownerGroupStringHelper.extractGroupFromBucketOwnerOrGroupString(genericInfoBucketData.getGroup()));

        List<KeyValueLabelMetadataEntity> metadataListWithGroupBucket = replaceOrAddGenericInfoKey(metadataListWithGroup,
                                                                                                   BUCKET_NAME_KEY,
                                                                                                   genericInfoBucketData.getBucketName());

        return new ArrayList<>(metadataListWithGroupBucket);
    }

    private List<KeyValueLabelMetadataEntity> replaceOrAddGenericInfoKey(
            final List<KeyValueLabelMetadataEntity> initialList, final String key, final String value) {
        if (key == null || value == null) {
            return initialList;
        }

        List<KeyValueLabelMetadataEntity> resultList = initialList.stream()
                                                                  .filter(item -> !item.getKey().equals(key))
                                                                  .collect(Collectors.toList());

        resultList.add(createKeyValueLabelMetadataEntity(key, value));

        return resultList;
    }

    private KeyValueLabelMetadataEntity createKeyValueLabelMetadataEntity(String key, String value) {
        return new KeyValueLabelMetadataEntity(key, value, WorkflowParser.ATTRIBUTE_GENERIC_INFORMATION_LABEL);
    }

    private AbstractMap.SimpleImmutableEntry<String, String>
            createSimpleImmutableEntry(final KeyValueLabelMetadataEntity KeyValueLabelMetadataEntity) {
        return new AbstractMap.SimpleImmutableEntry<>(KeyValueLabelMetadataEntity.getKey(),
                                                      KeyValueLabelMetadataEntity.getValue());

    }
}