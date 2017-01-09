package com.budilov

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.budilov.db.ESPictureService
import com.budilov.pojo.PictureItem
import com.budilov.rekognition.RekognitionService
import java.net.URLDecoder

/**
 * Created by Vladimir Budilov
 *
 * This Lambda function is invoked by S3 whenever an object is added to an S3 bucket.
 */
class AddPhotoLambda : RequestHandler<S3Event, String> {

    val rekognition = RekognitionService()
    val esService = ESPictureService()

    /**
     * 1. Get the s3 bucket and object name in question
     * 2. Clean the object name
     * 3. Run the RekognitionService service to get the labels
     * 4. Save the bucket/object & labels into ElasticSearch
     */
    override fun handleRequest(s3event: S3Event, context: Context): String {
        val record = s3event.getRecords().get(0)

        val srcBucket = record.getS3().getBucket().name

        // Object key may have spaces or unicode non-ASCII characters.
        var srcKeyEncoded = record.s3.`object`.key
                .replace('+', ' ')

        val srcKey = URLDecoder.decode(srcKeyEncoded, "UTF-8")
        println("bucket: " + srcBucket + " key: " + srcKey)

        // Get the cognito id from the object name (it's a prefix)
        val cognitoId = srcKey.split("/")[1]
        println("Cognito ID: " + cognitoId)

        val labels = rekognition.getLabels(srcBucket, srcKey)
        if (labels != null && labels.size > 0) {
            val picture = PictureItem(srcKeyEncoded.hashCode().toString(), srcBucket + Properties.getBucketSuffix() + "/" + srcKey, labels, null)
            println("Saving picture: " + picture)

            // Save the picture to ElasticSearch
            esService.add(cognitoId, picture)

        } else {
            println("No labels returned. Not saving to ES")
            //todo: create an actionable event to replay the action
        }

        return "Ok"
    }
}