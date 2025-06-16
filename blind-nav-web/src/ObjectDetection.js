import React, { useRef, useEffect, useState } from 'react';
import * as cocoSsd from '@tensorflow-models/coco-ssd';
import '@tensorflow/tfjs';

export default function ObjectDetection() {
  const videoRef = useRef(null);
  const [model, setModel] = useState(null);
  const [detected, setDetected] = useState('');

  useEffect(() => {
    // Load the model
    cocoSsd.load().then(setModel);

    // Start the camera
    navigator.mediaDevices.getUserMedia({ video: true }).then(stream => {
      videoRef.current.srcObject = stream;
    });
  }, []);

  useEffect(() => {
    let interval;
    if (model) {
      interval = setInterval(() => {
        detectFrame();
      }, 1500); // Detect every 1.5 seconds
    }
    return () => clearInterval(interval);
    // eslint-disable-next-line
  }, [model]);

  const detectFrame = async () => {
    if (!videoRef.current || !model) return;
    const predictions = await model.detect(videoRef.current);
    if (predictions.length > 0) {
      const label = predictions[0].class;
      setDetected(label);
      speak(label);
    }
  };

  const speak = (text) => {
    const utter = new window.SpeechSynthesisUtterance(`Detected: ${text}`);
    window.speechSynthesis.speak(utter);
  };

  return (
    <div>
      <video ref={videoRef} autoPlay width={400} height={300} style={{ border: '2px solid black' }} />
      <div style={{ fontSize: 24, marginTop: 10 }}>
        {detected && `Detected: ${detected}`}
      </div>
    </div>
  );
} 