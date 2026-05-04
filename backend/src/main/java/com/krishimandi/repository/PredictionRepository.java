package com.krishimandi.repository;

import com.krishimandi.model.Prediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionRepository extends MongoRepository<Prediction, String> {

    Page<Prediction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<Prediction> findByCropNameAndStateOrderByCreatedAtDesc(String cropName, String state);

    @Query("{ 'cropName': ?0, 'state': ?1, 'predictionRangeDays': ?2, 'createdAt': { $gte: ?3 } }")
    Optional<Prediction> findRecentPrediction(String cropName, String state,
                                               int rangeDays, LocalDateTime since);

    long countByUserId(String userId);

    @Query("{ 'userId': ?0, 'actualPrice': { $exists: true, $ne: null } }")
    List<Prediction> findWithActualPrices(String userId);

    @Aggregation(pipeline = {
        "{ $group: { _id: '$cropName', count: { $sum: 1 } } }",
        "{ $sort: { count: -1 } }",
        "{ $limit: 10 }"
    })
    List<CropCount> findTopCrops();

    @Query("{ 'createdAt': { $gte: ?0, $lte: ?1 } }")
    List<Prediction> findByDateRange(LocalDateTime from, LocalDateTime to);

    interface CropCount {
        String getId();
        int getCount();
    }
}
