#include "../../app/src/main/cpp/dsp_engine.cpp"
#include <cstdio>
#include <vector>
#include <cmath>

static double goertzelMag(const float* x, int frames, float freq, float fs){
    double w = 2.0*M_PI*freq/fs;
    double coeff = 2.0*std::cos(w);
    double s0=0.0, s1=0.0;
    for(int i=0;i<frames;i++){ double xi = (double)x[2*i]; double s2=s1; s1=s0; s0=xi+coeff*s1-s2; }
    double p = s1*s1+s0*s0-coeff*s1*s0;
    return std::sqrt(p);
}

static double goertzelGainDb(const float* out, int frames, const float* in, float freq, float fs){
    double mo = goertzelMag(out,frames,freq,fs), mi = goertzelMag(in,frames,freq,fs);
    return 20.0*log10(mo/mi);
}

int main(){
  JNIEnv env; const int frames=4800*4; std::vector<float> buf(frames*2);
  env.data=buf.data(); env.len=buf.size();

  // ---------------- Fase 4: Pruebas unitarias de todos los módulos ----------------

  // 1. EQ 25 bandas & Limitador
  Java_com_auraline_app_audio_NativeEngine_nativeInit(&env,nullptr,48000);
  Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(&env,nullptr,1);
  Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(&env,nullptr,-1.0f);
  Java_com_auraline_app_audio_NativeEngine_nativeSetCompressor(&env,nullptr, -60.0f, 1.0f, 10.0f, 100.0f, 0.0f);
  Java_com_auraline_app_audio_NativeEngine_nativeSetReverb(&env,nullptr, 0.0f, 0.3f);
  Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(&env,nullptr,25);
  for(int i=0;i<25;i++){ float f=30.f*std::pow(16000.f/30.f,i/24.f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetEqBand(&env,nullptr,i,f,24.f,1.41f);}
  Java_com_auraline_app_audio_NativeEngine_nativeSetPreamp(&env,nullptr,0.f);
  std::fill(buf.begin(),buf.end(),0.f);
  for(int k=0;k<10;k++) Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
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
  printf("1. EQ & Limitador: pico salida = %.4f (%.2f dBFS), reduccion max = %.1f dB\n",maxOut,20*log10(maxOut),maxGR);

  // 2. Prueba nula (EQ plano)
  Java_com_auraline_app_audio_NativeEngine_nativeInit(&env,nullptr,48000);
  Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(&env,nullptr,1);
  Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(&env,nullptr,-1.0f);
  Java_com_auraline_app_audio_NativeEngine_nativeSetCompressor(&env,nullptr, -60.0f, 1.0f, 10.0f, 100.0f, 0.0f);
  Java_com_auraline_app_audio_NativeEngine_nativeSetReverb(&env,nullptr, 0.0f, 0.3f);
  Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(&env,nullptr,25);
  for(int i=0;i<25;i++) Java_com_auraline_app_audio_NativeEngine_nativeSetEqBand(&env,nullptr,i,1000.f,0.f,1.41f);
  std::fill(buf.begin(),buf.end(),0.f);
  for(int k=0;k<10;k++) Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
  std::vector<float> in(frames*2); for(int i=0;i<frames;i++){float v=0.5f*std::sin(2*M_PI*440*i/48000.f); in[2*i]=in[2*i+1]=v; buf[2*i]=buf[2*i+1]=v;}
  Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
  double err=0;
  for(int i=kLookahead+100;i<frames;i++){ err=std::max(err,(double)std::fabs(buf[2*i]-in[2*(i-kLookahead)])); }
  printf("2. Prueba nula (EQ plano): error max = %.3g (%.1f dB)\n",err,20*log10(err+1e-30));

  // 3. Prueba vertical 60Hz +12dB
  {
    Java_com_auraline_app_audio_NativeEngine_nativeInit(&env,nullptr,48000);
    Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(&env,nullptr,1);
    Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(&env,nullptr,0.0f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(&env,nullptr,25);
    Java_com_auraline_app_audio_NativeEngine_nativeSetEqBand(&env,nullptr,0,60.f,12.f,1.41f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetPreamp(&env,nullptr,0.f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetCompressor(&env,nullptr, -60.0f, 1.0f, 10.0f, 100.0f, 0.0f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetReverb(&env,nullptr, 0.0f, 0.3f);
    std::fill(buf.begin(),buf.end(),0.f);
    std::vector<float> inBuf(frames*2);
    for(int k=0;k<10;k++) Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
    float vout=0.f;
    for(int blk=0;blk<5;blk++){
      for(int i=0;i<frames;i++){ float t=(blk*frames+i)/48000.f;
        float v=0.25f*std::sin(2*M_PI*60*t);
        buf[2*i]=v; buf[2*i+1]=v; inBuf[2*i]=v; inBuf[2*i+1]=v; }
      Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
      for(int i=0;i<frames*2;i++) vout=std::max(vout,std::fabs(buf[i]));
    }
    const float vin=0.25f;
    float gainDb=20*log10(vout/vin);
    float goertzelGainDbVal = goertzelGainDb(buf.data(), frames, inBuf.data(), 60.f, 48000.f);
    printf("3. Prueba vertical 60Hz +12dB: pico in=%.4f pico out=%.4f ganancia=%.2f dB | goertzel=%.2f dB\n",vin,vout,gainDb,goertzelGainDbVal);
    if (!(gainDb >= 10.0f && gainDb <= 13.0f)) { printf("FALLO: ganancia fuera de rango\n"); return 3; }
  }

  // 4. Módulo Tono
  {
    Java_com_auraline_app_audio_NativeEngine_nativeInit(&env,nullptr,48000);
    Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(&env,nullptr,1);
    Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(&env,nullptr,0.0f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(&env,nullptr,0);
    Java_com_auraline_app_audio_NativeEngine_nativeSetPreamp(&env,nullptr,0.f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetCompressor(&env,nullptr, -60.0f, 1.0f, 10.0f, 100.0f, 0.0f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetReverb(&env,nullptr, 0.0f, 0.3f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetTone(&env,nullptr,6.f,100.f, 0.f,8000.f, 0.f, 60.f);
    std::fill(buf.begin(),buf.end(),0.f);
    for(int k=0;k<10;k++) Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
    float vout=0.f;
    for(int blk=0;blk<5;blk++){
      for(int i=0;i<frames;i++){ float t=(blk*frames+i)/48000.f;
        float v=0.25f*std::sin(2*M_PI*100*t);
        buf[2*i]=v; buf[2*i+1]=v; }
      Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
      for(int i=0;i<frames*2;i++) vout=std::max(vout,std::fabs(buf[i]));
    }
    float toneGainDb=20*log10(vout/0.25f);
    printf("4. Prueba tono (shelf graves +6dB @100Hz): pico in=0.25 pico out=%.4f ganancia=%.2f dB\n",vout,toneGainDb);
  }

  // 5. Módulo Compresor
  {
    Java_com_auraline_app_audio_NativeEngine_nativeInit(&env,nullptr,48000);
    Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(&env,nullptr,1);
    Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(&env,nullptr,0.0f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(&env,nullptr,0);
    Java_com_auraline_app_audio_NativeEngine_nativeSetPreamp(&env,nullptr,0.f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetCompressor(&env,nullptr, -12.0f, 2.0f, 10.0f, 100.0f, 0.0f);
    std::fill(buf.begin(),buf.end(),0.f);
    for(int k=0;k<10;k++) Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
    float vout=0.f;
    for(int blk=0;blk<5;blk++){
      for(int i=0;i<frames;i++){ float t=(blk*frames+i)/48000.f;
        float v=0.9f*std::sin(2*M_PI*1000*t);
        buf[2*i]=v; buf[2*i+1]=v; }
      Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
      for(int i=0;i<frames*2;i++) vout=std::max(vout,std::fabs(buf[i]));
    }
    float compGainDb=20*log10(vout/0.9f);
    printf("5. Prueba compresor (thr -12dB ratio2 makeup0): pico in=0.9 pico out=%.4f ganancia=%.2f dB\n",vout,compGainDb);
  }

  // 6. Módulo Puerta (gate)
  {
    Java_com_auraline_app_audio_NativeEngine_nativeInit(&env,nullptr,48000);
    Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(&env,nullptr,1);
    Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(&env,nullptr,0.0f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(&env,nullptr,0);
    Java_com_auraline_app_audio_NativeEngine_nativeSetPreamp(&env,nullptr,0.f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetGate(&env,nullptr, -30.0f, 1.0f, 10.0f, 100.0f, 6.0f);
    std::fill(buf.begin(),buf.end(),0.f);
    for(int k=0;k<10;k++) Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
    float vout=0.f;
    for(int blk=0;blk<5;blk++){
      for(int i=0;i<frames;i++){ float t=(blk*frames+i)/48000.f;
        float v=0.01f*std::sin(2*M_PI*1000*t);
        buf[2*i]=v; buf[2*i+1]=v; }
      Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
      for(int i=0;i<frames*2;i++) vout=std::max(vout,std::fabs(buf[i]));
    }
    float gateGainDb=20*log10(vout/0.01f);
    printf("6. Prueba gate (thr -30dB makeup6): pico in=0.01 pico out=%.4f ganancia=%.2f dB\n",vout,gateGainDb);
  }

  // 7. Módulo Anchura Estéreo (Mid/Side: Mono 0% vs Estéreo 100% vs Expansión 200%)
  {
    Java_com_auraline_app_audio_NativeEngine_nativeInit(&env,nullptr,48000);
    Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(&env,nullptr,1);
    Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(&env,nullptr,0.0f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(&env,nullptr,0);
    Java_com_auraline_app_audio_NativeEngine_nativeSetPreamp(&env,nullptr,0.f);

    // Probar Mono (0.0)
    Java_com_auraline_app_audio_NativeEngine_nativeSetStereoWidth(&env,nullptr,0.0f);
    for(int blk=0;blk<3;blk++){
      for(int i=0;i<frames;i++){ buf[2*i]=0.8f; buf[2*i+1]=0.2f; }
      Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
    }
    float diffMono = std::fabs(buf[kLookahead * 2] - buf[kLookahead * 2 + 1]);

    // Probar 100% (1.0)
    Java_com_auraline_app_audio_NativeEngine_nativeSetStereoWidth(&env,nullptr,1.0f);
    for(int blk=0;blk<3;blk++){
      for(int i=0;i<frames;i++){ buf[2*i]=0.8f; buf[2*i+1]=0.2f; }
      Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
    }
    float diff100 = std::fabs(buf[kLookahead * 2] - buf[kLookahead * 2 + 1]);

    printf("7. Prueba anchura estéreo: mono (L-R diff)=%.4f (esperado 0.0), 100%% (L-R diff)=%.4f (esperado 0.60)\n", diffMono, diff100);
    if (diffMono > 0.001f) { printf("FALLO: mono no es simétrico\n"); return 7; }
  }

  // 8. Módulo Reverb (0% mix vs 100% mix)
  {
    Java_com_auraline_app_audio_NativeEngine_nativeInit(&env,nullptr,48000);
    Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(&env,nullptr,1);
    Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(&env,nullptr,0.0f);
    Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(&env,nullptr,0);

    // 0% mix -> idéntico a seco (dry)
    Java_com_auraline_app_audio_NativeEngine_nativeSetReverb(&env,nullptr,0.0f,0.5f);
    for(int blk=0;blk<3;blk++){
      for(int i=0;i<frames;i++){ buf[2*i]=0.5f; buf[2*i+1]=0.5f; }
      Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(&env,nullptr,&buf,frames);
    }
    float dryVal = buf[kLookahead * 2]; // Muestra retrasada por el lookahead del limitador

    printf("8. Prueba reverb 0%% mix: salida=%.4f (esperado 0.5000)\n", dryVal);
  }

  return maxOut<=1.0f?0:2;
}
