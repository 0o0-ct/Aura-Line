#include "../../app/src/main/cpp/dsp_engine.cpp"
#include <cstdio>
#include <vector>
int main(){
  JNIEnv env; const int frames=4800*4; std::vector<float> buf(frames*2);
  env.data=buf.data(); env.len=buf.size();
  Java_com_auraline_app_audio_NativeEngine_nativeInit(&env,nullptr,48000);
  Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(&env,nullptr,1);
  Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(&env,nullptr,-1.0f);
  Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(&env,nullptr,25);
  // 25 bands log-spaced 30..16k, todas a +24 dB
  for(int i=0;i<25;i++){ float f=30.f*std::pow(16000.f/30.f,i/24.f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetEqBand(&env,nullptr,i,f,24.f,1.41f);}
  Java_com_auraline_app_audio_NativeEngine_nativeSetPreamp(&env,nullptr,0.f);
  // Calentar la rampa de coeficientes: 1 s de silencio
  std::fill(buf.begin(),buf.end(),0.f);
  for(int k=0;k<10;k++) Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
  // Señal 0 dBFS: seno 1 kHz + seno 100 Hz full scale (clipeado a 1.0) y ruido
  float maxOut=0, maxGR=0;
  for(int blk=0;blk<5;blk++){
    for(int i=0;i<frames;i++){ float t=(blk*frames+i)/48000.f;
      float v=std::sin(2*M_PI*1000*t)+std::sin(2*M_PI*100*t)+0.3f*std::sin(2*M_PI*6000*t);
      v=std::max(-1.f,std::min(1.f,v)); buf[2*i]=v; buf[2*i+1]=v; }
    float pk=Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
    maxOut=std::max(maxOut,pk);
    maxGR=std::min(maxGR,Java_com_auraline_app_audio_NativeEngine_nativeGainReductionDb(&env,nullptr));
    bool bad=false; for(float x:buf) if(!std::isfinite(x)) bad=true; if(bad){printf("NaN!\n");return 1;}
  }
  printf("pico salida = %.4f (%.2f dBFS), reduccion max = %.1f dB\n",maxOut,20*log10(maxOut),maxGR);
  // Prueba nula: EQ plano -> salida == entrada (retrasada 240 muestras)
  for(int i=0;i<25;i++) Java_com_auraline_app_audio_NativeEngine_nativeSetEqBand(&env,nullptr,i,1000.f,0.f,1.41f);
  std::fill(buf.begin(),buf.end(),0.f);
  for(int k=0;k<10;k++) Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
  std::vector<float> in(frames*2); for(int i=0;i<frames;i++){float v=0.5f*std::sin(2*M_PI*440*i/48000.f); in[2*i]=in[2*i+1]=v; buf[2*i]=buf[2*i+1]=v;}
  Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
  double err=0; for(int i=kLookahead+100;i<frames;i++){ err=std::max(err,(double)std::fabs(buf[2*i]-in[2*(i-kLookahead)])); }
  printf("prueba nula (EQ plano): error max = %.3g (%.1f dB)\n",err,20*log10(err+1e-30));
  return maxOut<=1.0f?0:2;
}
