# BreastScan — AI Breast Cancer Prediction App

A smart, AI-powered mobile application for early breast cancer risk prediction using TensorFlow Lite, OCR, and image-based analysis.

---

## Features

- AI-powered prediction using tabular ML model  
- Image-based prediction using CNN (TensorFlow Lite)  
- OCR-based report analysis (Google ML Kit)  
- Automatic extraction of values from medical reports  
- Form-based prediction with clinical parameters  
- Quick Self-Check for awareness and early detection  
- Displays prediction with confidence score  
- Works offline with on-device ML  
- Clean and intuitive Material UI  
- Secure user authentication  
- Educational section for breast cancer awareness  
- Error handling and input validation  

---

## Tech Stack

- Frontend: Android (Java, XML)  
- Machine Learning: TensorFlow Lite  
- OCR: Google ML Kit  
- Architecture: Modular Android Architecture  
- Build: Gradle  
- IDE: Android Studio  

---

## App Workflow

1. User opens the application  
2. Chooses prediction method:  
   - Manual data entry  
   - Upload medical report (OCR)  
   - Upload image  
   - Quick Self-Check  
3. Data is preprocessed and normalized  
4. TensorFlow Lite model performs prediction  
5. Result displayed with classification and confidence score  

---

## Project Structure

```bash
app/
 ├── java/com/breastscan/
 ├── assets/ (TFLite models)
 ├── res/ (UI layouts, drawables)
 ├── ml/ (model handling classes)
```

---

## Setup & Run

### Prerequisites
- Android Studio (latest)  
- Android SDK installed  
- Java 8+  

### Steps

```bash
git clone <your-repo-link>
cd BreastScan
```

1. Open project in Android Studio  
2. Sync Gradle  
3. Connect Android device / Emulator  
4. Run the app  

---

## Modules

- User Authentication Module  
- Form-Based Prediction Module  
- OCR Report Analysis Module  
- Image-Based Prediction Module  
- Quick Self-Check Module  
- Result Display Module  
- User Profile & Menu Module  

---

## Advantages

- Fast and real-time predictions  
- Supports multiple input methods  
- Reduces manual effort using OCR  
- Works offline (no internet required)  
- Promotes awareness and early detection  
- Easy-to-use interface  

---

## Limitations

- Accuracy depends on input data and model  
- OCR may fail on low-quality images  
- Not a replacement for professional medical diagnosis  

---

## Future Enhancements

- Improve model accuracy with larger datasets  
- Cloud integration for advanced analytics  
- iOS version support  
- Telemedicine integration  
- Multilingual support  

---

## Developed By

Smriti Prajapati  

---

## Note

This application is intended for educational and preliminary assessment purposes only. It should not be used as a substitute for professional medical advice or diagnosis.
