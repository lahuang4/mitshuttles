package edu.mit.scripts.lahuang4.mitshuttles;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by Lauren on 1/30/2016.
 */
public interface NextBus {
    @GET("publicXMLFeed")
    Call<ShuttleList.ConfigBody> getConfig(@Query("command") String command,
                                           @Query("a") String agent);

    @GET("publicXMLFeed")
    Call<ShuttleList.ConfigBody> getConfig(@Query("command") String command,
                                           @Query("a") String agent, @Query("r") String route);

    @GET("publicXMLFeed")
    Call<ShuttleSchedule.PredictionBody> getPrediction(@Query("command") String command,
                                                       @Query("a") String agent,
                                                       @Query("r") String route,
                                                       @Query("s") String stop);

    @GET("publicXMLFeed")
    Call<ShuttleSchedule.PredictionBody> getMultiplePredictions(@Query("command") String command,
                                                                @Query("a") String agent,
                                                                @Query("stops") List<String> stops);
}