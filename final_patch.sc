s.reboot;
s.options.sampleRate = 48000;

/*
initialize var playingProb = 1 and targetPlayingProb = 1 for java module to control sound without phone
A copy of the StkUGens folder should be placed in the directory shown when running:
Platform.userExtensionDir;
This folder is included in the same directory as this patch

N.b. MUST UNCOMMENT MIC PATCH LINE

Bus 0 & 1 stero out
Bus 2 & 3 in
Bus 4 & 5 private buses for reverb in
Bus 6 private bus w/ LF noise on it

On windows, you may need to modify directories (see line 46)

//one thing to be careful of:

even if the audio server is rebooted, the lang side ~pulse schedule
seems to keep running, which can give a faulty impression of how dense a texture is
during development and lead to server crashes?

*/
s.queryAllNodes;
Server.default.options.memSize
Server.default.options.memSize = 128000;//8192 is the default, but considerd old fashoned
//https://github.com/supercollider/supercollider/issues/5558
(
var freqs = [256 * 2.pow(-5/12.0),256,256 * 2.pow(5/12.0),256 * 2.pow(6/12.0),256 * 2.pow(11/12.0),256 * 2.pow(16/12.0)];

var absPulses  = [0,0,0,0,0,0];
var lastPulseTimes = [0,0,0,0,0,0];
var lastAbsTimes = [0,0,0,0,0,0];
var ply1 = [true,true,true,true,true,true];
var durs =  [9/8.0, 45/32.0, 3/2.0, 15/8.0, 2, 135/64.0];
var durUsedInVoice = [2,0,5,4,1,3];
var  dblDurs  = [0,0,0,0,0,0];//4
var isRunning = [true,true,true,true,true,true];//[false, false, false, false, false, false];
var  rhythOffSet = [0,0,0,0,0,0];
var dbls = [0,0,0,0,0,0];
var graceNotes = false;
var graceVal = 0.35;
var correctOffSetMode = 0;
var oneNoteMode = false;
var schedule = [0,0,0,0,0,0];
var droneFreq = 0;
var drone;
var endDrone  = false;
var endTexture = false;
var thinTexture = false;
var textAmp = 1;
var playingProb = 0;//initialize to true if java module is controlling
var targetPlayingProb = 0;
var foregroundPlaying = false;
var flurry = Array.new(100);
//java module and sc module should still be in sync about which voices are on and off
//we want texturing processes to run continuously, only stop sound within pulse and drone routines
//the above variables are used to make this happen correctly


~routines = [nil,nil,nil,nil,nil,nil];
~thisdir = thisProcess.nowExecutingPath.dirname;


TempoClock.default.tempo = 18;//12;//6;

~chord = {
	Synth.grain(\chord, [\freq, droneFreq]);
};

~pulse = {
	arg voice = 0, freq = freqs[voice], del = 0, absTime  = 0, myDur, nOfPulse  =  1, schTime, cont = true;
	var amp;


	if(ply1[voice].not,{
		freq = freqs[(voice + 3) % 6];
		if(freq > freqs[voice], {freq = freq / 2.0}, {freq =  freq * 2.0});
	});

	//Synth.grain(\simpleNote, [\freq, freq, \amp, ((2 * pi * (absPulses[voice] % 150) / 150.0).sin / 12.0)]);// + pi?
	amp = ((pi + 2 * pi * (absPulses[voice] % 150) / 150.0).sin / 6.0);

	//"amp: ".post;
	//amp.postln;
	if(thinTexture, {
		amp = amp * textAmp;
	});

	if(1.0.rand <= playingProb, {
		Synth.grain(\xylo, [\freq, freq, \amp, amp, \pan, amp * 6.0]);
	}, {
		//"Skip PLAY".post;playingProb.post;targetPlayingProb.postln;
	});
	if(playingProb < targetPlayingProb,{
		playingProb = playingProb + 0.001;
	},
	/*{
		if(playingProb > targetPlayingProb,{
			playingProb = playingProb - 0.05;
		});
	}*/
	);


	//"pulse".postln;
	ply1[voice] = ply1[voice].not;
	absPulses[voice] = absPulses[voice] + 1;

	if(endTexture, {
		dbls[voice] = dbls[voice] + 1;
		"dbls: ".post;
		dbls[voice].post;
		"; voice: ".post;
		voice.postln;
	});

	myDur =  durs[voice] * 2.pow(durUsedInVoice[voice] / 2.0);//dblDurs[voice]);

	absTime =  ((absPulses[voice] + nOfPulse) * myDur);
	del = absTime.round(1.0) - (absPulses[voice] * myDur).round(1.0);

	del = del * 2.pow(dbls[voice]);
	//"del: ".post;del.postln;
	if((del == 0), {del = 1});//just in case!!!! (hasn't been a problem)
	if((del > 60),
		{"DEL OVER 30".postln;
			nil},
		{
			//"del: ".post;del.postln;
			//if((del < 1), {"del: ".post;del.postln;});
			del;})
	//if(isRunning[voice].not, {nil})
};


~drone = Routine({
	//"HI".postln;
	loop{
		Synth.grain(\xylo, [\freq, freqs[0] / 2.0,\amp, 0.06]);
		//"HI".postln;
		1.yield;
	}
});



~b0 = Buffer.read(s, ~thisdir +/+ "xyloA.wav");

~b1 = Buffer.read(s, ~thisdir +/+ "xylo_granular.wav");

~b2 = Buffer.read(s, ~thisdir +/+ "Fantasy for Vagrant Flute.wav");

SynthDef.new(\simpleNote,{
	arg freq, amp = 0.01, pan = 0.5;
	var sig, ratio;
	ratio = freq / 440;
	Out.ar(0,[amp * pan,amp * ( 1 - pan)] * PlayBuf.ar(1,~b0, ratio, doneAction: Done.freeSelf));
}).add;

SynthDef.new(\reverb, {
	arg in=4, lpf1=2000, lpf2=6000, predel=0.025, out=0, dec=3.5, mix=1.0;
	var dry, wet, sig;
	//dec = MouseY.kr(3.5,60);
	//mix = MouseY.kr(0.1,0.9);
	/*
	dec = EnvGen.ar(Env.new([30,30,3.5, 3.5, 60,30,3.5,3.5,30],[0,135,136,229.7,230,240,255,285,295]), doneAction: 0);
	mix = EnvGen.ar(Env.new([0.9,0.9,0.08,0.08,1,0.9,0.08,0.08,0.9],[0,135,136,229.7,230,240,255,285,295]), doneAction: 0);
	*/
	dry = In.ar(in, 2);
	wet = In.ar(in, 2);
	wet = DelayN.ar(wet, 0.5, predel.clip(0.0001,0.5));
	wet = 16.collect{
		var temp;
		temp = CombL.ar(
			wet,
			0.1,
			LFNoise1.kr({ExpRand(0.02,0.04)}!2).exprange(0.02,0.099),
			dec
		);
		temp = LPF.ar(temp, lpf1);
	}.sum * 0.25;
	8.do{
		wet = AllpassL.ar(
			wet,
			0.1,
			LFNoise1.kr({ExpRand(0.02,0.04)}!2).exprange(0.02,0.099),
			dec
		);
	};
	wet = LeakDC.ar(wet);
	wet = LPF.ar(wet, lpf2, 0.5);
	sig = dry.blend(wet, mix);
	Out.ar(out, sig);
}).add;

SynthDef.new(\micPatch, {
	arg sig, pan;
	pan = LFNoise2.ar(1 / (3.0));
	//sig = PlayBuf.ar(2, ~b2);//UNCOMMENT THIS TO GET MIDI SOUND
	//sig = In.ar(2,1);//UNCOMMENT THIS TO GET MIC SOUND!//2,1 might be needed (1chan inputt)

	sig = Mix.ar(sig);
	sig = sig * 0.5;
	Out.ar([0,4], Pan2.ar(sig, pan));
}).add;

SynthDef(\xylo, { |freq=440, gate=1, amp=0.3, sustain=0.5, pan=0, inst = 1, dur = 3.0|
	var sig, env, stTime;

	stTime = Select.kr((freq>250) + (freq>90) + (freq>75), [0.01, 0.002, 0.0015,0]);
	sig = Select.ar((freq>1450), [StkBandedWG.ar(freq, instr:inst, mul:3), PlayBuf.ar(1,~b0, freq/880)]);
	env = EnvGen.kr(Env.new([0.001, 1,0.001],[stTime, 1],[\exp]), gate: gate, timeScale: dur, doneAction: 2);

	Out.ar([0,4], Pan2.ar(sig, pan, env * amp));
}).add;


SynthDef.new(\drone,{
	arg freq, amp = 0.01, pan = 0.5;
	var sig, ratio, env, buff;
	ratio = freq / 440;
	env = Env.new([0,0.01,1,0.01,0],[0.01,0.5,0.99,1],[\linear,\exponential,\exponential,\linear]);

	buff = ~b1;

	Out.ar([0,4],[amp * pan,amp * ( 1 - pan)] * PlayBuf.ar(1,buff, ratio, loop: 1) * EnvGen.kr(env, timeScale: 30, doneAction: Done.freeSelf));
}).add;

//change ratio to EnvGen.kr(Env.new([1stFreq,2ndFreq],[0.5]), timeScale: 15, doneAction:Done.freeSelt)
SynthDef.new(\chord,{
	arg freq, amp = 0.3, pan = 0.5;
	var sig, ratio1, ratio2, ratio3, env, buff, rat = 4 * freq / 440;

	//rat = 1 * 440 / 440;

	//rat.postln;

	ratio1 = Env.new([0,rat,rat * 15.0 / 16.0],[0.5,1], curve: 'step');
	ratio2 = Env.new([0,rat *  3 / 2.0,rat * 25 / 16.0],[0.5,1], curve: 'step');
	ratio3 = Env.new([0,rat * 12 / 5.0,rat * 5 / 2.0],[0.5,1], curve: 'step');
	//ratio = Env.new([1,1.25],[0.5], curve: 'step');
	env = Env.new([0,0.01,1,0.01,0],[0.01,0.5,0.99,1],[\linear,\exponential,\exponential,\linear]);

	buff = ~b1;

	Out.ar(0,[amp * pan,amp * ( 1 - pan)] *
		(PlayBuf.ar(1,buff, EnvGen.kr(ratio1, timeScale: 15), loop: 1) +
			PlayBuf.ar(1,buff, EnvGen.kr(ratio2, timeScale: 15), loop: 1) +
			PlayBuf.ar(1,buff, EnvGen.kr(ratio3, timeScale: 15), loop: 1)
	) * EnvGen.kr(env, timeScale: 15, doneAction: Done.freeSelf));
}).add;

SynthDef.new(\mel,{
	arg freq;
	var sig, ratio;
	ratio = freq / 440;
	Out.ar(0,[0.01,0.01] * PlayBuf.ar(1,~b0, ratio, doneAction: Done.freeSelf));
}).add;

n = NetAddr("127.0.0.1", NetAddr.langPort);
o.free;
//msg format: /texture, msgType, voice,  data1, data2,  etc.
o = OSCFunc.newMatching({
	arg msg, time, addr, recvPort;
	var voice = msg[2], status = msg[1];
	//"HELLO OSC".postln;
	//msg.postln;
	//msg.postln;

	if(voice <  6, {
		switch(status,
			0,{//start
				if(endTexture.not,{
					if(~routines[voice] == nil,{
						//"new rout".postln;
						~routines[voice] = Routine({
							var val = 0;
							while({val != nil}, {
								val = ~pulse.value(voice);
								val.yield;
							});
						});
						~routines[voice].play(TempoClock.default);
					});
					//"Start".postln;
				});
			},
			1, {//stop
				~routines[voice].stop;
				~routines[voice] = nil;
				//"Stop".postln;
			},
			2, {//adjust freq
				var oct;
				oct = ((freqs[voice] /msg[3]).log2).round(1.0);
				freqs[voice] = msg[3]* (2.pow(oct));
				/*
				switch([0,1,2].choose,
				1,{
				dblDurs[voice] = dblDurs[voice] + 1;
				//durs[voice] = durs[voice] * 2;
				},
				2,{
				dblDurs[voice] = dblDurs[voice] - 1;
				//durs[voice] = durs[voice] / 2;
				});
				*/
			},

			3, {//grace  notes on
				graceNotes = true;
			},
			4, {//grace  notes off
				graceNotes = false;
			},
			5, {//oneNote  on
				oneNoteMode  = true;
			},
			6, {//oneNote  off
				oneNoteMode  = false;
			},

			7, {//adjust dblDurs
				dblDurs[voice] = dblDurs[voice] + msg[3];
				dblDurs.postln;
			},
			8, {// initialize music
				var schedular = Scheduler(SystemClock);

				Synth.grain(\micPatch);
				"mic patch started".postln;
				//"INITIALIZE".postln;


				/*
				schedular.sched(420,{ending =  true; nil});
				schedular.sched(430,{~routines[2].stop; nil});
				schedular.sched(440,{~routines[4].stop; nil});
				schedular.sched(450,{~routines[5].stop; nil});
				schedular.sched(460,{~routines[3].stop; nil});
				schedular.sched(465,{~routines[1].stop; nil});
				schedular.sched(467,{~routines[0].stop; nil});
				*/


			},
			9, {//adjust durUsedInVoice
				durUsedInVoice[voice] = durUsedInVoice[voice] + msg[3];
			},
			10, {// set drone freq
				//"set Drone".postln;
				droneFreq = freqs[voice] / 4.0;
				//droneFreq.postln;
			},
			11, {// end drone
				endDrone = true;
				"END DRONE".postln;
			},
			12, {// start drone
				drone = Routine({
					while({endDrone.not}, {

						Synth.grain(\drone, [\freq, droneFreq, \amp,0.05.rrand(0.3)]);//0.2,0.3
						if(endDrone, {
							nil.yield;
						},{
							0.rrand(6 * 20, 6 * 60).yield;
						});
					});
				});
				drone.play(TempoClock.default);
			},
			13, {// thin texture
				thinTexture = true;
				"THIN TEXTURE".postln;
			},
			14, {// end texture
				endTexture = true;
				"END TEXTURE".postln;
			}
		);
	},{
		if(status == 2,{
			if(foregroundPlaying, {
				Synth.grain(\xylo, [\freq, msg[3] * 2.pow(((5 * msg[5]).rand).round(1.0)),
					\amp, 0.05 * msg[4],\pan, -1.rrand(1) * (1 - msg[4])]);//amp?
				textAmp =  msg[4];
			}, {
				while({flurry.size > 50},
					{
						flurry.removeAt(flurry.size - 1);
				});
				flurry = flurry.insert(0,msg);
			}
			);
		});
	});


},'/texture');

p.free;

~curCue = 0;

p = OSCFunc.newMatching({
	arg msg;
	if(msg[1] == 1000, {
		switch(~curCue,
			0, {
				var schedular = Scheduler(SystemClock);
				var fluries = 0;
				while({flurry.size > 0},
					{
						var msg = flurry.removeAt(flurry.size - 1);
						{
							Synth.grain(\xylo, [\freq, msg[3] * 2.pow(((5 * msg[5]).rand).round(1.0)),
								\amp, 0.05 * msg[4],\pan, -1.rrand(1) * (1 - msg[4])]);//amp?
							textAmp =  msg[4];
							"fl".post; fluries.postln;
							msg.postln;
						}.defer(0.05 * fluries);
						fluries = fluries + 1;
				});
				{
					foregroundPlaying = true;
				}.defer(0.05 * fluries);

				Synth.grain(\micPatch);
				"mic patch started".postln;
				//add a buffer that sounds like the beginning
			},
			1, {
				"START TEXTURE".postln;
				"TARGET:".post;
				targetPlayingProb.postln;
				"PLAYING:".post;
				playingProb.postln;
				targetPlayingProb = 1;

			},
			2, {
				TempoClock.default.tempo = 24;//change tempo in drone sect
				drone = Routine({
					while({endDrone.not}, {

						Synth.grain(\drone, [\freq, droneFreq, \amp,0.05.rrand(0.3)]);//0.2,0.3
						if(endDrone, {
							nil.yield;
						},{
							0.rrand(6 * 20, 6 * 60).yield;
						});
					});
				});
				drone.play(TempoClock.default);
			},
			3, {
				thinTexture = true;
				"THIN TEXTURE".postln;
			},
			4, {
				endTexture = true;
				"END TEXTURE".postln;
			},
			5, {
				endDrone = true;
				"END DRONE".postln;
			},
			6, {
				endDrone = true;
				targetPlayingProb = 0;
				playingProb = 0;
				foregroundPlaying = false;
			}

		);
		~curCue = ~curCue + 1;
	});
},'/mrmr/pushbutton/0/jedermann');

{
	Synth.grain(\reverb);
	"reverb started".postln;
}.defer(1);

)



Synth.grain(\reverb);




Synth.grain(\drone, [\freq, 110, \amp,0.2.rrand(0.8)]);//0.2,0.3



(
~routines[0].stop;
~routines[0] = nil;
)

thisProcess.platform.recordingsDir

0.rrand(0.2,0.8)

0.rrand(6 * 20, 6 * 60)

0.2.rrand(0.8)

1.0.rand

100.rand

x = Rand()
x

Synth.grain(\xylo, [\freq, 440,\amp, 0.5]);

/*
using phone for osc
the path is ''/mrmr/pushbutton/0/jedermann''  when the button is pressed, msg[1] should be 1000, when released it's 0

java side, start sound should be pressed at the beginning, but initialize osc could be removed.  We could attack to the initialize message a flurry of sounds to replace what had been hapening when the clock first started and play music first started; prior to inizialize we could add logic SC-side to prevent sounds.
Java-side, startTexture could happen on a timer.

all other osc messages can be activated by the phone instead of the java modules.  when the final message is sent, we can add logic to the SC-side to prevent new sounds or even stop responding to java osc
*/



