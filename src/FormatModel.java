import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class FormatModel implements Observable{
    
    private ArrayList<Observer> observers = new ArrayList<Observer>();
    
    private double increment;
    private double sample_size;
    private double frameCount;
    private double num;
    private double denom;
    private double maxBitrate, avgBitrate;
    private double duration;
    
    private ArrayList<Double> samples = new ArrayList<Double>();
    
    public FormatModel(double num, double denom, double inc, double size, double duration) {
        this.num = num;
        this.denom = denom;
        this.increment = inc;
        this.sample_size = size;
        this.duration = duration;
        
        this.frameCount = Math.round(duration * (num / denom));
        
        //System.out.format("Input file: frames: %.0f, frame rate: %.3f/%.3f\n1 frame = %.2fms, sample = %.2fms\n", frameCount, num, denom, increment, sample_size);
    }
    
    public void setMaxBitrate(double max) {
        this.maxBitrate = max;
    }

    public double getMaxBitrate() {
        return maxBitrate;
    }

    public void setAverageBitrate(double avg) {
        this.avgBitrate = avg;
        notifyObservers();
    }

    public double getAverageBitrate() {
        return avgBitrate; 
    }

    public void setSamples(ArrayList<Double> samples) {
        this.samples = samples;
    }

    public ArrayList<Double> getSamples(){
        return samples;
    }

    public double getInc() {
        return increment;
    }

    public double getSampleSize() {
        return sample_size;
    }

    public double getDuration() {
        return frameCount;
    }

    @Override
    public void addObserver(Observer o) {
        observers.add(o);
    }

    @Override
    public void notifyObservers() {
        for(Observer o: observers) {
            o.update(this);
        }
    }

}
