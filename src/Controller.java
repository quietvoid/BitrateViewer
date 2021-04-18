import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.prefs.Preferences;

import javax.print.attribute.standard.Media;

import org.json.JSONObject;

public class Controller {
    
    FormatModel media;
    View view;
    
    ArrayList<Double> samples = new ArrayList<Double>();
    String ffprobePath;
    String inputFile;
    Process pr;
    
    public Controller() {
        ffprobePath = readPreference();
        this.inputFile = "";
    }

    public void setView(View view) {
        this.view = view;
    }

    public void setFFProbePath(String path) {
        this.ffprobePath = path;
    }

    public void setInputFile(File file) {
        String path = file.getAbsolutePath();
        
        this.inputFile = path;
        setModelParams();
    }

    public synchronized void setModelParams() {
        String[] probeArgs = {ffprobePath, "-v", "quiet", "-print_format", "json", "-select_streams", "v", "-show_format", "-show_streams", "-hide_banner", inputFile};
        String[] bitrateArgs = {ffprobePath, "-select_streams", "v", "-show_entries", "packet=size:stream=duration", "-of", "compact=p=0:nk=1", inputFile};
        
        String jsonStr = FFProbeQuery(probeArgs);
        
        JSONObject json = new JSONObject(jsonStr);
        JSONObject format = (JSONObject) json.get("format");
        JSONObject streams = (JSONObject) json.getJSONArray("streams").get(0);
        
        String fps = streams.getString("avg_frame_rate");
        double duration = Double.parseDouble(format.getString("duration"));
        
        String[] framerate = fps.split("/");
        double num = Double.parseDouble(framerate[0]);
        double denom = Double.parseDouble(framerate[1]);
        
        if(denom > 1000) {
            num /= 1000;
            denom /= 1000;
        }
        
        double frame_increment = 1000/(num/denom);
        double frame_size = Math.round(num * frame_increment);
        
        this.media = new FormatModel(num, denom, frame_increment, frame_size, duration);
        media.addObserver(view);
        
        view.setSampleCount(Math.round(media.getDuration()/(num/denom)));
        
        System.out.println("Scanning frames with FFprobe...");
        String execTime = calculateBitrate(bitrateArgs);
        
        
        try {
            view.plotGraph();
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
        
        System.out.format("Max: %.2f kb/s, average: %.2f kb/s, exec time: %s\n", media.getMaxBitrate(), media.getAverageBitrate(), execTime);
        System.gc();
        Thread.currentThread().interrupt();
    }
    
    public String formatTime(long time) {
        long seconds, minutes;
        String execTime = "";
        
        if(time > 1000) {
            seconds=(time/1000)%60;
            minutes=((time-seconds)/1000)/60;
            execTime = String.format("%dm %ds", minutes, seconds);
        }
        else {
            execTime = String.format("%d ms", time);
        }
        return execTime;
    }

    public String FFProbeQuery(String[] args) {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        
        StringBuilder sb = new StringBuilder();
        try {
            Process dP = pb.start();
            
            String line;
            BufferedReader in = new BufferedReader(new InputStreamReader(dP.getInputStream()));
            
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            dP.waitFor();
            in.close();
            
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
        
        return sb.toString();
    }

    private String calculateBitrate(String[] args) {
        ProcessBuilder bitrateBuilder = new ProcessBuilder(args);

        bitrateBuilder.redirectErrorStream(true);
        
        long startTime = System.currentTimeMillis();
        
        try {
            
            samples.clear();
            
            pr = bitrateBuilder.start();
            
            String line;
            BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            
            double increment = media.getInc();
            double sample_size = media.getSampleSize();
            double duration = media.getDuration();
            
            double counter = 0;
            double sample = 0;
            double remain = 0;
            double remaincount = 0;
            
            double cur_max = 0;
            double avg_bitrate = 0;
            int i = 0;
            
            while ((line = in.readLine()) != null) {
                if(line.matches("^[0-9]+$")) {
                    i++;
                    counter += increment;
                    
                    double frameSize = (Double.parseDouble(line) * 8.0)/1000.0;
                    
                    sample += frameSize;
                    
                    if(Math.round(counter) == sample_size) {
                        //System.out.println((i/duration) * 100+"%");
                        if(cur_max < sample) {
                            cur_max = sample;
                            
                            //Set new max every sample if one is higher.
                            media.setMaxBitrate(cur_max);
                        }
                        avg_bitrate += sample;
                        
                        samples.add(sample);
                        
                        //Update average in model every sample.
                        media.setAverageBitrate(avg_bitrate/samples.size());
                        media.setSamples(samples);
                        
                        //Reset for next sample
                        sample = 0;
                        counter = 0;
                        remain = 0;
                        remaincount = 0;
                    }
                    else {
                        remain += frameSize;
                        remaincount += increment;
                    }
                }
            }
            
            pr.waitFor();
            samples.add(remain);
            
            if(cur_max < remain) {
                cur_max = remain;
                
                //Set new max every sample if one is higher.
                media.setMaxBitrate(cur_max);
            }
            
            //Remaining frames < 1 second total
            avg_bitrate += remain;
            media.setAverageBitrate(avg_bitrate/(samples.size() - (1 * remaincount/sample_size)));
            
            in.close();
            
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
        
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        
        return formatTime(elapsedTime);
    }
    
    public void stopProcess() {
        pr.destroy();
    } 

    public void savePreference(String path) {
        Preferences prefs = Preferences.userNodeForPackage(BitrateViewer.class);

        this.ffprobePath = path;
        prefs.put("BitrateViewer-FFProbePath", path);
    }

    public String readPreference() {
        Preferences prefs = Preferences.userNodeForPackage(BitrateViewer.class);

        return prefs.get("BitrateViewer-FFProbePath", "./ffprobe.exe");
    }
}
