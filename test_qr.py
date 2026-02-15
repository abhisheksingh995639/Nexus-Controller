import qrcode
from PIL import Image
import sys

qr = qrcode.QRCode()
qr.add_data("test")
qr.make()
img = qr.make_image()
print(f"Type: {type(img)}")
print(f"Is Instance of PIL.Image.Image: {isinstance(img, Image.Image)}")

try:
    img_conv = img.convert("RGB")
    print(f"Converted Type: {type(img_conv)}")
    print(f"Converted Is Instance: {isinstance(img_conv, Image.Image)}")
except Exception as e:
    print(f"Convert failed: {e}")

# Check for .get_image()
if hasattr(img, 'get_image'):
    print("Has get_image()")
else:
    print("No get_image()")
