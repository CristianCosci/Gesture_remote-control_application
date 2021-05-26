package com.example.telecomandogesture;

import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ServerSocket serverSocket;
    private Socket tempClientSocket;
    Thread serverThread = null;
    public static final int SERVER_PORT = 3003; //porta alla quale effettuare connessione al server
    private LinearLayout msgList;
    private Handler handler;
    private int greenColor;

    private boolean waspiano = true;
    private boolean notFirstTime = true;
    private float lastx,lasty,lastz,differencex,differencey,differencez, max=15.0f; //assi x, y, z per sensori di movimento
    private Accelerometer accelerometer; //oggetto che permette l'utilizzo dell'accelerometro per rilevare le gesture
    private Gyroscope gyroscope; //oggetto che permetto l'utilizzo del giroscopio per rilevare le gesture

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //settaggio layout all'apertura dell'app
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Telecomando gesture");
        greenColor = ContextCompat.getColor(this, R.color.colorAccent);
        handler = new Handler();
        msgList = findViewById(R.id.msgList);

        accelerometer = new Accelerometer(this);
        gyroscope = new Gyroscope(this);
        //metodo che riconosce la gesture "shake" mediante l'accelerometro e l'asse z, inviando poi il messaggio al client
        accelerometer.setListener(new Accelerometer.Listener() {
            @Override
            public void onTranslation(float tx, float ty, float tz) {
                if(notFirstTime){
                    differencez=Math.abs(lastz-tz);
                    if(differencez > max){
                        sendMessage("shake");
                    }
                }
                lastz=tz;
                notFirstTime=true;
            }
        });
        //metodo che riconosce le gesture di rotazione e inclinazione mediante il giroscopip e gli assi x, y, z, inviando poi il messaggio al client
        gyroscope.setListener(new Gyroscope.Listener() {
            @Override
            public void onRotation(float rx, float ry, float rz) {
                if(rx>2.0f && waspiano){//telefono inclinato in giu
                    waspiano=false;
                    sendMessage("giu");
                }else if(rx<-2.0f  && waspiano){//telefono inclinato in su
                    waspiano=false;
                    sendMessage("su");
                } else if(ry>2.0f  && waspiano){//ruoto il telefono verso destra
                    waspiano=false;
                    sendMessage("destra");
                } else if(ry<-2.0f  && waspiano){//ruoto il telefono verso sinistra
                    waspiano=false;
                    sendMessage("sinistra");
                } else if((rx<=1.0f)&&(rx>=-1.0f)&&(ry>=-1.0f)&&(ry<=1.0f)&&(rz<=1.0f)&&(rz>=-1.0f)&&(!waspiano)){
                    waspiano=true;
                    //sendMessage("il telefono è sul tavolo");
                } else if(rz<-2.0f  && waspiano) {//ruoto il telefono in senso orario
                    waspiano = false;
                    sendMessage("chiudi");
                    try { //prima di chiudere l'app attendo 1 secondo
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //in base alla versione android del dispositivo vi è un diverso metodo di chiusura dell'app. Nel caso di
                    //android superiore alla verione LOLLIPOP l'applicazione viene chiusa e rimossa dai task, altrimenti viene solo chiusa
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        finishAndRemoveTask();
                    } else{
                        finish();
                    }
                }
            }
        });
    }
    @Override
    protected void onResume(){
        super.onResume();
        accelerometer.register();
        gyroscope.register();
    }

    //funzione per agire sulla text wiew
    public TextView textView(String message, int color) {
        if (null == message || message.trim().isEmpty()) {
            message = "<Empty Message>";
        }
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setText(message + " [" + getTime() +"]");
        tv.setTextSize(20);
        tv.setPadding(0, 5, 0, 0);
        return tv;
    }

    //funzione per mostrare i messaggi sulla textWiew
    public void showMessage(final String message, final int color) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                msgList.addView(textView(message, color));
            }
        });
    }

    //funzione per agire in base ai click sui bottoni
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.start_server) { //bottone per avviare il server
            msgList.removeAllViews();
            showMessage("Server Started.", Color.BLACK);
            this.serverThread = new Thread(new ServerThread());
            this.serverThread.start();
            return;
        }
    }

    private void sendMessage(final String message) { //funzione per inviare messaggi al client mediante bufferWriter
        try {
            if (null != tempClientSocket) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        PrintWriter out = null;
                        try {
                            out = new PrintWriter(new BufferedWriter(
                                    new OutputStreamWriter(tempClientSocket.getOutputStream())),
                                    true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        out.println(message);
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //funzioni relative al server
    class ServerThread implements Runnable {

        public void run() {
            Socket socket;
            try {
                serverSocket = new ServerSocket(SERVER_PORT); //creazione del socket
                findViewById(R.id.start_server).setVisibility(View.GONE);
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Starting Server : " + e.getMessage(), Color.RED);
            }
            if (null != serverSocket) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket = serverSocket.accept();
                        CommunicationThread commThread = new CommunicationThread(socket);
                        new Thread(commThread).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                        showMessage("Error Communicating to Client :" + e.getMessage(), Color.RED);
                    }
                }
            }
        }
    }

    //thread dedicati alla comunicazione con ogni client nel caso fossero multipli
    class CommunicationThread implements Runnable {

        private Socket clientSocket;

        private BufferedReader input;

        public CommunicationThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            tempClientSocket = clientSocket;
            try {
                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Connecting to Client!!", Color.RED);
            }
            showMessage("Connected to Client!!", greenColor);
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    setContentView(R.layout.table);
                }
            });
        }

        public void run() {

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = input.readLine();
                    if (null == read || "Disconnect".contentEquals(read)) {
                        Thread.interrupted();
                        read = "Client Disconnected";
                        showMessage("Client : " + read, greenColor);
                        break;
                    }
                    showMessage("Client : " + read, greenColor);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

    }

    public String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != serverThread) {
            sendMessage("Disconnect");
            serverThread.interrupt();
            serverThread = null;
        }
    }
}