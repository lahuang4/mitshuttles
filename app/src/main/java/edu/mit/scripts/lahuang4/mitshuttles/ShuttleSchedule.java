package edu.mit.scripts.lahuang4.mitshuttles;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.TextView;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.SimpleXmlConverterFactory;

public class ShuttleSchedule extends AppCompatActivity {

    private static final String TAG = "ShuttleSchedule";

    private String routeName;
    private ShuttleList.Route route;
    private Retrofit retrofit;
    private NextBus nextBus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        routeName = getIntent().getStringExtra("Route");
        route = ShuttleList.routes.get(routeName);
        setContentView(R.layout.activity_shuttle_schedule);
        TextView shuttleScheduleName = (TextView)findViewById(R.id.shuttle_schedule_text_name);
        shuttleScheduleName.setText(routeName);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        retrofit = new Retrofit.Builder()
                .baseUrl(Constants.NEXTBUS_URL)
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .build();
        nextBus = retrofit.create(NextBus.class);

        refreshShuttleSchedule();
    }

    @Root(name = "body")
    public static class PredictionBody {
        @Attribute
        String copyright;

        @ElementList(inline = true)
        List<Schedule> schedule;
    }

    @Root(name = "predictions", strict = false)
    public static class Schedule {
        @Element(name = "direction", required = false)
        Direction direction;

        @ElementList(inline = true)
        List<Message> message;

        @Attribute
        String agencyTitle;

        @Attribute
        String routeTitle;

        @Attribute
        String routeTag;

        @Attribute
        String stopTitle;

        @Attribute
        String stopTag;

        @Attribute(required = false)
        String dirTitleBecauseNoPredictions;
    }

    @Root(name = "direction")
    public static class Direction {
        @ElementList(inline = true)
        List<Prediction> predictions;

        @Attribute
        String title;
    }

    @Root(name = "message")
    public static class Message {
        @Attribute
        String text;

        @Attribute
        String priority;
    }

    @Root(name = "prediction")
    public static class Prediction {
        @Attribute
        String epochTime;

        @Attribute
        private String seconds;

        @Attribute
        private String minutes;

        @Attribute(required = false)
        private String isDeparture;

        @Attribute(required = false)
        String affectedByLayover;

        @Attribute
        String dirTag;

        @Attribute
        String vehicle;

        @Attribute
        String block;

        public int getSeconds() {
            return Integer.parseInt(seconds);
        }

        public int getMinutes() {
            return Integer.parseInt(minutes);
        }
    }

    private void refreshShuttleSchedule() {
        Log.d(TAG, "Getting schedule for " + route.tag);
        List<String> stops = new ArrayList<>();
        for (ShuttleList.Stop stop : route.stops) {
            stops.add(route.tag + "|" + stop.tag);
        }
        Call<PredictionBody> call = nextBus.getMultiplePredictions("predictionsForMultiStops", "mit", stops);
//        ShuttleList.Stop stop = route.stops.get(0);
//            Call<PredictionBody> call = nextBus.getPrediction("predictions", "mit", route.tag,
//                    stop.tag);
            call.enqueue(new Callback<PredictionBody>() {
                @Override
                public void onResponse(Response<PredictionBody> response) {
                    for (Schedule schedule : response.body().schedule) {
//                        Schedule schedule = response.body().schedule;
                        if (schedule.dirTitleBecauseNoPredictions != null) {
                            Log.d(TAG, "Shuttle " + routeName + " is not currently running or NextBus is down.");
                        } else {
                            for (Message message : schedule.message) {
                                Log.d(TAG, "Schedule message: " + message.text + ", priority " + message.priority);
                            }
                            if (schedule.direction.predictions != null) {
                                for (Prediction p : schedule.direction.predictions) {
                                    Log.d(TAG, "Schedule stop prediction: " + p.getMinutes() + " minutes, or " + p.getSeconds() + " seconds");
                                }
                            }
                            // TODO: Create visual elements on the view displaying the schedule
                        }
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                    if (t.getMessage().contains("Element 'Error' does not have a match")) {
                        Log.e(TAG, "Unable to retrieve information for current stop. Are you sure this stop is on the specified route?");
                    } else {
                        setContentView(R.layout.network_error_message);
                    }
                }
            });
    }

}
