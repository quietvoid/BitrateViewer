import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class View extends Application implements Observer{
    
    private Controller control;
    private FormatModel media;
    
    ExecutorService executor;
    
    private double maxBitrate;
    private double avgBitrate;
    private ArrayList<Double> samples = new ArrayList<Double>();
    
    Text maxLabel;
    Text maxValue;
    Text avgLabel;
    Text avgValue;
    Text curFile;
    
    BorderPane root;
    MenuItem chooserItem;
    MenuItem prefsItem;
    
    NumberAxis yAxis;
    NumberAxis xAxis;
    XYChart.Series<Integer, Double> series;
    XYChart.Data data;
    
    long curTime = 0;
    private double sampleCount = 0;
    private int graphCounter = 0;
    
    public View() {
        
    }
    
    public View(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        executor = Executors.newFixedThreadPool(1);
        primaryStage.setTitle("BitrateViewer");
        
        root = initUI();
        
        //Initialize Controller
        control = new Controller();
        control.setView(this);
        
        AreaChart<Integer, Double> areaChart = createChart();
        
        root.setCenter(areaChart);
        
        Scene scene = new Scene(root, 700, 300);
        scene.getStylesheets().add(getClass().getResource("resources/style.css").toExternalForm());
        
        
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
        
        primaryStage.setOnCloseRequest(e->{
            try {
                control.stopProcess();
                executor.shutdownNow();
                System.exit(0);
            }
            catch(Exception ex) {
                
            }
        });
        
        chooserItem.setOnAction(e->{
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Video File");
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

            //Reset series data.
            series.getData().clear();
            graphCounter = 0;
            
            try {
                File file = fileChooser.showOpenDialog(primaryStage);
                String filename = file.getName();
                filename = filename.substring(0, filename.lastIndexOf('.'));
                
                curFile.setText(filename);
                curTime = System.currentTimeMillis();
                
                startAnalysis(file);
            }
            catch(Exception ex) {
                
            }
        });

        prefsItem.setOnAction(e->{
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Video File");
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

            //Reset series data.
            series.getData().clear();
            samples.clear();
            
            try {
                File file = fileChooser.showOpenDialog(primaryStage);
                String path = file.getAbsolutePath();
                
                control.savePreference(path);
            }
            catch(Exception ex) {
                
            }
        });
    }
    
    @Override
    public void update(FormatModel o) {
        media = o;
        
        maxBitrate = o.getMaxBitrate();
        yAxis.setUpperBound((Math.ceil(maxBitrate / 1000.0) * 1000));
        
        avgBitrate = o.getAverageBitrate();
        samples = o.getSamples();
        
        updateValues();
    }

    private void updateValues() {
        Platform.runLater(new Runnable() {
              @Override public void run() {
                      
                      maxValue.setText(String.format("%.2f kb/s", maxBitrate));
                    avgValue.setText(String.format("%.2f kb/s", avgBitrate));
                    
                    if(!samples.isEmpty()) {
                        plotGraph();
                    }
              }
        });
    }

    private BorderPane initUI() {
        BorderPane root = new BorderPane();
        
        VBox topBox = new VBox();
        topBox.setBorder(new Border(new BorderStroke(Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK,
                BorderStrokeStyle.NONE, BorderStrokeStyle.NONE, BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE,
                CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY)));
        
        MenuBar menuBar = new MenuBar();
        
        Menu menuFile = new Menu("File");
        Menu menuPrefs = new Menu("Settings");
        
        chooserItem = new MenuItem("Select input file");
        prefsItem = new MenuItem("Select FFprobe path");
        
        menuFile.getItems().add(chooserItem);
        menuPrefs.getItems().add(prefsItem);
 
        menuBar.getMenus().addAll(menuFile, menuPrefs);
        
        VBox rightBox = new VBox();
        rightBox.setBorder(new Border(new BorderStroke(Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK,
                BorderStrokeStyle.NONE, BorderStrokeStyle.NONE, BorderStrokeStyle.NONE, BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY)));
        rightBox.setSpacing(50);
        
        VBox maxBox = new VBox();
        VBox avgBox = new VBox();
        maxBox.setPadding(new Insets(5,5,5,5));
        maxBox.setSpacing(10);
        
        avgBox.setPadding(new Insets(5,5,5,5));
        avgBox.setSpacing(10);
        
        maxLabel = new Text("Maximum bitrate:");
        maxLabel.setStyle("-fx-font-weight: bold");
        maxValue = new Text("0 kb/s");
        
        avgLabel = new Text("Average bitrate:");
        avgLabel.setStyle("-fx-font-weight: bold");
        avgValue = new Text("0 kb/s");
        
        curFile = new Text("");
        
        topBox.getChildren().addAll(menuBar, curFile);
        maxBox.getChildren().addAll(maxLabel, maxValue);
        avgBox.getChildren().addAll(avgLabel, avgValue);
        
        rightBox.getChildren().addAll(maxBox, avgBox);
        root.setTop(topBox);
        root.setRight(rightBox);
        
        return root;
    }
    
    private AreaChart<Integer, Double> createChart() {
        xAxis = new NumberAxis();
        xAxis.setLabel("Samples");
        xAxis.setAutoRanging(false);
        xAxis.setTickUnit(500);
        
        yAxis = new NumberAxis();
        yAxis.setAutoRanging(false);
        yAxis.setTickUnit(5000);

        AreaChart<Integer, Double> areaChart = new AreaChart(xAxis,yAxis);
        areaChart.setCreateSymbols(false);
        areaChart.setLegendVisible(false);
        areaChart.setAnimated(false);
        
        series = new XYChart.Series<Integer, Double>();

        areaChart.getData().add(series);
        
        return areaChart;
    }

    private void startAnalysis(File file) {
        Task task = new Task<Void>() {
            @Override public Void call() {
                control.setInputFile(file);
                return null;
            }
        };
        executor.submit(task);
    }

    public void plotGraph() {
        Platform.runLater(new Runnable() {
              @Override public void run() {
                    for(int i = graphCounter; i<samples.size(); i++) {
                        data = new XYChart.Data(i, samples.get(i));
                        data.setYValue(samples.get(i));
                        
                        series.getData().add(data);
                    }
                    graphCounter = samples.size()-1;
              }
        });
    }
    
    public void setSampleCount(double d) {
        Platform.runLater(new Runnable() {
              @Override public void run() {
                      sampleCount = d;
                    xAxis.setUpperBound(sampleCount);
              }
        });
    }
}
