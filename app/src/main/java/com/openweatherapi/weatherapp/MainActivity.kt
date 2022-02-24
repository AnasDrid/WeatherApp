package com.openweatherapi.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.openweatherapi.weatherapp.R
import com.openweatherapi.weatherapp.Service.WeatherService
import com.openweatherapi.weatherapp.models.WeatherResponse
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient

    private var mProgressDialog :Dialog?=null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {


        val current = LocalDateTime.now()

        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy ")
        val formatted = current.format(formatter)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        date.text=current.dayOfWeek.toString().lowercase().capitalize()+"  "+formatted.toString()
        setupUI()
        if (!isLocationEnabled()){
            Toast.makeText(this,"Your Location is turned off, Please turn it on",Toast.LENGTH_SHORT)
                .show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
           Dexter.withActivity(this).withPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,
               android.Manifest.permission.ACCESS_COARSE_LOCATION
           ).withListener(object : MultiplePermissionsListener{
               override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                   if (report!!.areAllPermissionsGranted()){
                       requestLocationData()
                   }
                   if (report.isAnyPermissionPermanentlyDenied) {
                       Toast.makeText(
                           this@MainActivity,
                           "You have denied location permission. In order for the app to work you need to allow Location.",
                            Toast.LENGTH_SHORT
                       ).show()

                   }

               }

               override fun onPermissionRationaleShouldBeShown(
                   permissions: MutableList<PermissionRequest>?,
                   token: PermissionToken?
               ) {
                   showRationalDialogForPermission()
               }
           }).onSameThread().check()
            }


    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){

        if(Constants.isNetworkAvailable(this)){

           val retrofit :Retrofit = Retrofit.Builder().baseUrl(Constants.Base_URL).addConverterFactory(GsonConverterFactory.create()).build()
            val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
            val listcall: Call<WeatherResponse> = service.getweather(latitude,longitude, Constants.METRIC_UNIT, Constants.APP_ID)
            showCustomProgressDialog()
            listcall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        hideProgressDialog()
                        val weatherList : WeatherResponse? = response.body()
                        Log.i("Response Result", "$weatherList")
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                    }
                    else{
                       val rc = response.code()
                        when(rc){
                            400 -> {Log.e("Error 400", "Bad Connection")}
                            404 -> {Log.e("Error 404", "Not Found")}
                            else -> {Log.e("Error", "Generic Error")}
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Failure", "Failed to get response:  "+ t!!.toString())
                }

            })
            }else
        {
            Toast.makeText(this@MainActivity,"Unable to connect to internet",Toast.LENGTH_SHORT).show()

        }
    }

    private val mLocationCallBack = object :LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation:Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("current Latitude","$latitude")
            val longitude = mLastLocation.longitude
            Log.i("current Longitude","$longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest= com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(mLocationRequest,mLocationCallBack, Looper.myLooper()!!
        )
    }

    private fun showRationalDialogForPermission(){
        AlertDialog.Builder(this).setMessage("In Order to get the weather forecast, this app needs permission to use your location, it can be enabled in settings").setPositiveButton("Go to Settings"){_,_->try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName,null)
            intent.data =uri
            startActivity(intent)
        }catch (e:ActivityNotFoundException){
            e.printStackTrace()
        }
        }.setNegativeButton("Cancel"){
            dialog,_->dialog.dismiss()
        }.show()
    }
    private fun isLocationEnabled(): Boolean {
        val locationManager:LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)


    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        mProgressDialog!!.show()
    }
    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }
    private fun setupUI() {

        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()) {

            val weatherList =
                Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)


            for (z in weatherList.weather.indices) {
                Log.i("NAMEEEEEEEE", weatherList.weather[z].main)

                tv_main.text = weatherList.weather[z].main
                tv_main_description.text = weatherList.weather[z].description
                tv_temp.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
                tv_min.text = weatherList.main.tempMin.toString() + " min"
                tv_max.text = weatherList.main.tempMax.toString() + " max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country
                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise.toLong())
                tv_sunset_time.text = unixTime(weatherList.sys.sunset.toLong())
                when (weatherList.weather[z].icon) {
                    "01d" -> iv_main.setImageResource(R.drawable._1d)
                    "01n" -> iv_main.setImageResource(R.drawable.clear_night)
                    "02d" -> iv_main.setImageResource(R.drawable._2d)
                    "02n" -> iv_main.setImageResource(R.drawable._2n)
                    "03d" -> iv_main.setImageResource(R.drawable._3)
                    "03n" -> iv_main.setImageResource(R.drawable._3)
                    "04d" -> iv_main.setImageResource(R.drawable._4d)
                    "04n" -> iv_main.setImageResource(R.drawable._4n)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "10n" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable._1)
                    "11n" -> iv_main.setImageResource(R.drawable._1)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                }
            }
        }

    }
    private fun getUnit(value: String): String? {
        Log.i("unit", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat") val sdf =
            SimpleDateFormat("HH:mm:ss")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}