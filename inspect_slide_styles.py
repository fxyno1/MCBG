import os
from pptx import Presentation

def inspect_slide_styles(path):
    prs = Presentation(path)
    # 取 Slide 2 或者是 Slide 5 进行分析
    slide = prs.slides[2] 
    print("--- Slide 2 Shapes ---")
    for shape in slide.shapes:
        print(f"Shape: {shape.name}, Type: {shape.shape_type}")
        if shape.has_text_frame:
            tf = shape.text_frame
            for p in tf.paragraphs:
                for r in p.runs:
                    print(f"  Text: '{r.text}'")
                    if r.font.name:
                        print(f"    Font Name: {r.font.name}")
                    if r.font.size:
                        print(f"    Font Size: {r.font.size.pt} pt")
                    if r.font.color and r.font.color.type:
                        print(f"    Font Color Type: {r.font.color.type}")
                        if r.font.color.type == 1: # RGB
                            print(f"    Font Color RGB: {r.font.color.rgb}")

if __name__ == '__main__':
    path = r"C:\Users\Dell\Downloads\图像压缩.pptx"
    inspect_slide_styles(path)
