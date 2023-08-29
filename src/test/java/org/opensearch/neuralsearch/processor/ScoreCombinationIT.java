/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.neuralsearch.processor;

import static org.opensearch.neuralsearch.TestUtils.assertHybridSearchResults;
import static org.opensearch.neuralsearch.TestUtils.assertWeightedScores;
import static org.opensearch.neuralsearch.TestUtils.createRandomVector;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import lombok.SneakyThrows;

import org.junit.After;
import org.junit.Before;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.knn.index.SpaceType;
import org.opensearch.neuralsearch.common.BaseNeuralSearchIT;
import org.opensearch.neuralsearch.query.HybridQueryBuilder;
import org.opensearch.neuralsearch.query.NeuralQueryBuilder;

import com.google.common.primitives.Floats;

public class ScoreCombinationIT extends BaseNeuralSearchIT {
    private static final String TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME = "test-neural-multi-doc-one-shard-index";
    private static final String TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME = "test-neural-multi-doc-three-shards-index";
    private static final String TEST_QUERY_TEXT3 = "hello";
    private static final String TEST_QUERY_TEXT4 = "place";
    private static final String TEST_QUERY_TEXT7 = "notexistingwordtwo";
    private static final String TEST_DOC_TEXT1 = "Hello world";
    private static final String TEST_DOC_TEXT2 = "Hi to this place";
    private static final String TEST_DOC_TEXT3 = "We would like to welcome everyone";
    private static final String TEST_DOC_TEXT4 = "Hello, I'm glad to you see you pal";
    private static final String TEST_DOC_TEXT5 = "Say hello and enter my friend";
    private static final String TEST_KNN_VECTOR_FIELD_NAME_1 = "test-knn-vector-1";
    private static final String TEST_TEXT_FIELD_NAME_1 = "test-text-field-1";
    private static final int TEST_DIMENSION = 768;
    private static final SpaceType TEST_SPACE_TYPE = SpaceType.L2;
    private static final String SEARCH_PIPELINE = "phase-results-pipeline";
    private final float[] testVector1 = createRandomVector(TEST_DIMENSION);
    private final float[] testVector2 = createRandomVector(TEST_DIMENSION);
    private final float[] testVector3 = createRandomVector(TEST_DIMENSION);
    private final float[] testVector4 = createRandomVector(TEST_DIMENSION);

    private static final String L2_NORMALIZATION_METHOD = "l2";
    private static final String HARMONIC_MEAN_COMBINATION_METHOD = "harmonic_mean";
    private static final String GEOMETRIC_MEAN_COMBINATION_METHOD = "geometric_mean";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareModel();
    }

    @After
    @SneakyThrows
    public void tearDown() {
        super.tearDown();
        deleteSearchPipeline(SEARCH_PIPELINE);
        findDeployedModels().forEach(this::deleteModel);
    }

    /**
     * Using search pipelines with result processor configs like below:
     * {
     *     "description": "Post processor for hybrid search",
     *     "phase_results_processors": [
     *         {
     *             "normalization-processor": {
     *                 "normalization": {
     *                     "technique": "min-max"
     *                 },
     *                 "combination": {
     *                     "technique": "arithmetic_mean",
     *                     "parameters": {
     *                         "weights": [
     *                             0.4, 0.7
     *                         ]
     *                     }
     *                 }
     *             }
     *         }
     *     ]
     * }
     */
    @SneakyThrows
    public void testArithmeticWeightedMean_whenWeightsPassed_thenSuccessful() {
        initializeIndexIfNotExist(TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME);
        // check case when number of weights and sub-queries are same
        createSearchPipeline(
            SEARCH_PIPELINE,
            DEFAULT_NORMALIZATION_METHOD,
            DEFAULT_COMBINATION_METHOD,
            Map.of(PARAM_NAME_WEIGHTS, Arrays.toString(new float[] { 0.6f, 0.5f, 0.5f }))
        );

        HybridQueryBuilder hybridQueryBuilder = new HybridQueryBuilder();
        hybridQueryBuilder.add(QueryBuilders.termQuery(TEST_TEXT_FIELD_NAME_1, TEST_QUERY_TEXT3));
        hybridQueryBuilder.add(QueryBuilders.termQuery(TEST_TEXT_FIELD_NAME_1, TEST_QUERY_TEXT4));
        hybridQueryBuilder.add(QueryBuilders.termQuery(TEST_TEXT_FIELD_NAME_1, TEST_QUERY_TEXT7));

        Map<String, Object> searchResponseWithWeights1AsMap = search(
            TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
            hybridQueryBuilder,
            null,
            5,
            Map.of("search_pipeline", SEARCH_PIPELINE)
        );

        assertWeightedScores(searchResponseWithWeights1AsMap, 0.375, 0.3125, 0.001);

        // delete existing pipeline and create a new one with another set of weights
        deleteSearchPipeline(SEARCH_PIPELINE);
        createSearchPipeline(
            SEARCH_PIPELINE,
            DEFAULT_NORMALIZATION_METHOD,
            DEFAULT_COMBINATION_METHOD,
            Map.of(PARAM_NAME_WEIGHTS, Arrays.toString(new float[] { 0.8f, 2.0f, 0.5f }))
        );

        Map<String, Object> searchResponseWithWeights2AsMap = search(
            TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
            hybridQueryBuilder,
            null,
            5,
            Map.of("search_pipeline", SEARCH_PIPELINE)
        );

        assertWeightedScores(searchResponseWithWeights2AsMap, 0.606, 0.242, 0.001);

        // check case when number of weights is less than number of sub-queries
        // delete existing pipeline and create a new one with another set of weights
        deleteSearchPipeline(SEARCH_PIPELINE);
        createSearchPipeline(
            SEARCH_PIPELINE,
            DEFAULT_NORMALIZATION_METHOD,
            DEFAULT_COMBINATION_METHOD,
            Map.of(PARAM_NAME_WEIGHTS, Arrays.toString(new float[] { 0.8f }))
        );

        Map<String, Object> searchResponseWithWeights3AsMap = search(
            TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
            hybridQueryBuilder,
            null,
            5,
            Map.of("search_pipeline", SEARCH_PIPELINE)
        );

        assertWeightedScores(searchResponseWithWeights3AsMap, 0.357, 0.285, 0.001);

        // check case when number of weights is more than number of sub-queries
        // delete existing pipeline and create a new one with another set of weights
        deleteSearchPipeline(SEARCH_PIPELINE);
        createSearchPipeline(
            SEARCH_PIPELINE,
            DEFAULT_NORMALIZATION_METHOD,
            DEFAULT_COMBINATION_METHOD,
            Map.of(PARAM_NAME_WEIGHTS, Arrays.toString(new float[] { 0.6f, 0.5f, 0.5f, 1.5f }))
        );

        Map<String, Object> searchResponseWithWeights4AsMap = search(
            TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
            hybridQueryBuilder,
            null,
            5,
            Map.of("search_pipeline", SEARCH_PIPELINE)
        );

        assertWeightedScores(searchResponseWithWeights4AsMap, 0.375, 0.3125, 0.001);
    }

    /**
     * Using search pipelines with config for harmonic mean:
     * {
     *     "description": "Post processor for hybrid search",
     *     "phase_results_processors": [
     *         {
     *             "normalization-processor": {
     *                 "normalization": {
     *                     "technique": "l2"
     *                 },
     *                 "combination": {
     *                     "technique": "harmonic_mean"
     *                 }
     *             }
     *         }
     *     ]
     * }
     */
    @SneakyThrows
    public void testHarmonicMeanCombination_whenOneShardAndQueryMatches_thenSuccessful() {
        initializeIndexIfNotExist(TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME);
        createSearchPipeline(
            SEARCH_PIPELINE,
            DEFAULT_NORMALIZATION_METHOD,
            HARMONIC_MEAN_COMBINATION_METHOD,
            Map.of(PARAM_NAME_WEIGHTS, Arrays.toString(new float[] { 0.8f, 0.7f }))
        );
        String modelId = getDeployedModelId();

        HybridQueryBuilder hybridQueryBuilderDefaultNorm = new HybridQueryBuilder();
        hybridQueryBuilderDefaultNorm.add(new NeuralQueryBuilder(TEST_KNN_VECTOR_FIELD_NAME_1, "", modelId, 5, null, null));
        hybridQueryBuilderDefaultNorm.add(QueryBuilders.termQuery(TEST_TEXT_FIELD_NAME_1, TEST_QUERY_TEXT3));

        Map<String, Object> searchResponseAsMapDefaultNorm = search(
            TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
            hybridQueryBuilderDefaultNorm,
            null,
            5,
            Map.of("search_pipeline", SEARCH_PIPELINE)
        );

        assertHybridSearchResults(searchResponseAsMapDefaultNorm, 5, new float[] { 0.5f, 1.0f });

        deleteSearchPipeline(SEARCH_PIPELINE);

        createSearchPipeline(
            SEARCH_PIPELINE,
            L2_NORMALIZATION_METHOD,
            HARMONIC_MEAN_COMBINATION_METHOD,
            Map.of(PARAM_NAME_WEIGHTS, Arrays.toString(new float[] { 0.8f, 0.7f }))
        );

        HybridQueryBuilder hybridQueryBuilderL2Norm = new HybridQueryBuilder();
        hybridQueryBuilderL2Norm.add(new NeuralQueryBuilder(TEST_KNN_VECTOR_FIELD_NAME_1, "", modelId, 5, null, null));
        hybridQueryBuilderL2Norm.add(QueryBuilders.termQuery(TEST_TEXT_FIELD_NAME_1, TEST_QUERY_TEXT3));

        Map<String, Object> searchResponseAsMapL2Norm = search(
            TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
            hybridQueryBuilderL2Norm,
            null,
            5,
            Map.of("search_pipeline", SEARCH_PIPELINE)
        );
        assertHybridSearchResults(searchResponseAsMapL2Norm, 5, new float[] { 0.5f, 1.0f });
    }

    /**
     * Using search pipelines with config for geometric mean:
     * {
     *     "description": "Post processor for hybrid search",
     *     "phase_results_processors": [
     *         {
     *             "normalization-processor": {
     *                 "normalization": {
     *                     "technique": "l2"
     *                 },
     *                 "combination": {
     *                     "technique": "geometric_mean"
     *                 }
     *             }
     *         }
     *     ]
     * }
     */
    @SneakyThrows
    public void testGeometricMeanCombination_whenOneShardAndQueryMatches_thenSuccessful() {
        initializeIndexIfNotExist(TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME);
        createSearchPipeline(
            SEARCH_PIPELINE,
            DEFAULT_NORMALIZATION_METHOD,
            GEOMETRIC_MEAN_COMBINATION_METHOD,
            Map.of(PARAM_NAME_WEIGHTS, Arrays.toString(new float[] { 0.8f, 0.7f }))
        );
        String modelId = getDeployedModelId();

        HybridQueryBuilder hybridQueryBuilderDefaultNorm = new HybridQueryBuilder();
        hybridQueryBuilderDefaultNorm.add(new NeuralQueryBuilder(TEST_KNN_VECTOR_FIELD_NAME_1, "", modelId, 5, null, null));
        hybridQueryBuilderDefaultNorm.add(QueryBuilders.termQuery(TEST_TEXT_FIELD_NAME_1, TEST_QUERY_TEXT3));

        Map<String, Object> searchResponseAsMapDefaultNorm = search(
            TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
            hybridQueryBuilderDefaultNorm,
            null,
            5,
            Map.of("search_pipeline", SEARCH_PIPELINE)
        );

        assertHybridSearchResults(searchResponseAsMapDefaultNorm, 5, new float[] { 0.5f, 1.0f });

        deleteSearchPipeline(SEARCH_PIPELINE);

        createSearchPipeline(
            SEARCH_PIPELINE,
            L2_NORMALIZATION_METHOD,
            GEOMETRIC_MEAN_COMBINATION_METHOD,
            Map.of(PARAM_NAME_WEIGHTS, Arrays.toString(new float[] { 0.8f, 0.7f }))
        );

        HybridQueryBuilder hybridQueryBuilderL2Norm = new HybridQueryBuilder();
        hybridQueryBuilderL2Norm.add(new NeuralQueryBuilder(TEST_KNN_VECTOR_FIELD_NAME_1, "", modelId, 5, null, null));
        hybridQueryBuilderL2Norm.add(QueryBuilders.termQuery(TEST_TEXT_FIELD_NAME_1, TEST_QUERY_TEXT3));

        Map<String, Object> searchResponseAsMapL2Norm = search(
            TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
            hybridQueryBuilderL2Norm,
            null,
            5,
            Map.of("search_pipeline", SEARCH_PIPELINE)
        );
        assertHybridSearchResults(searchResponseAsMapL2Norm, 5, new float[] { 0.5f, 1.0f });
    }

    private void initializeIndexIfNotExist(String indexName) throws IOException {
        if (TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME.equalsIgnoreCase(indexName) && !indexExists(TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME)) {
            prepareKnnIndex(
                TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
                Collections.singletonList(new KNNFieldConfig(TEST_KNN_VECTOR_FIELD_NAME_1, TEST_DIMENSION, TEST_SPACE_TYPE)),
                1
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
                "1",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector1).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_DOC_TEXT1)
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
                "2",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector2).toArray())
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
                "3",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector3).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_DOC_TEXT2)
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
                "4",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector4).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_DOC_TEXT3)
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME,
                "5",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector4).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_DOC_TEXT4)
            );
            assertEquals(5, getDocCount(TEST_MULTI_DOC_INDEX_ONE_SHARD_NAME));
        }

        if (TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME.equalsIgnoreCase(indexName) && !indexExists(TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME)) {
            prepareKnnIndex(
                TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
                Collections.singletonList(new KNNFieldConfig(TEST_KNN_VECTOR_FIELD_NAME_1, TEST_DIMENSION, TEST_SPACE_TYPE)),
                3
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
                "1",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector1).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_DOC_TEXT1)
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
                "2",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector2).toArray())
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
                "3",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector3).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_DOC_TEXT2)
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
                "4",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector4).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_DOC_TEXT3)
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
                "5",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector4).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_DOC_TEXT4)
            );
            addKnnDoc(
                TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME,
                "6",
                Collections.singletonList(TEST_KNN_VECTOR_FIELD_NAME_1),
                Collections.singletonList(Floats.asList(testVector4).toArray()),
                Collections.singletonList(TEST_TEXT_FIELD_NAME_1),
                Collections.singletonList(TEST_DOC_TEXT5)
            );
            assertEquals(6, getDocCount(TEST_MULTI_DOC_INDEX_THREE_SHARDS_NAME));
        }
    }
}