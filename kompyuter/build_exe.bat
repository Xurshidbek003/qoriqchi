@echo off
chcp 65001 >nul
echo === Ekran qo'riqchisi: .exe yig'ilmoqda ===
python -m pip install --upgrade pip
python -m pip install -r requirements.txt pyinstaller
python -m PyInstaller --noconfirm --onefile --windowed ^
  --name "EkranQoriqchisi" ^
  --icon "models\ikonka.ico" ^
  --add-data "models;models" ^
  ekran_qoriqchisi.py
echo.
echo Tayyor: dist\EkranQoriqchisi.exe
pause
