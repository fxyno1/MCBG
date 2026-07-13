import os
import json
from pptx import Presentation

def extract_text_from_ppt(path):
    prs = Presentation(path)
    data = []
    
    for slide_idx, slide in enumerate(prs.slides):
        slide_data = {
            "slide_index": slide_idx,
            "shapes": []
        }
        
        for shape_idx, shape in enumerate(slide.shapes):
            shape_info = {
                "shape_index": shape_idx,
                "paragraphs": []
            }
            
            # 处理普通文本框
            if shape.has_text_frame:
                for p_idx, paragraph in enumerate(shape.text_frame.paragraphs):
                    if paragraph.text.strip():
                        shape_info["paragraphs"].append({
                            "type": "text_frame",
                            "p_index": p_idx,
                            "text": paragraph.text
                        })
            
            # 处理表格
            if shape.has_table:
                for r_idx, row in enumerate(shape.table.rows):
                    for c_idx, cell in enumerate(row.cells):
                        for p_idx, paragraph in enumerate(cell.text_frame.paragraphs):
                            if paragraph.text.strip():
                                shape_info["paragraphs"].append({
                                    "type": "table",
                                    "row": r_idx,
                                    "col": c_idx,
                                    "p_index": p_idx,
                                    "text": paragraph.text
                                })
            
            # 处理组合形状
            if shape.shape_type == 6: # GroupShape
                # 简单递归提取子形状
                sub_shapes_data = []
                for sub_idx, sub_shape in enumerate(shape.shapes):
                    sub_info = {
                        "sub_index": sub_idx,
                        "paragraphs": []
                    }
                    if sub_shape.has_text_frame:
                        for p_idx, paragraph in enumerate(sub_shape.text_frame.paragraphs):
                            if paragraph.text.strip():
                                sub_info["paragraphs"].append({
                                    "type": "text_frame",
                                    "p_index": p_idx,
                                    "text": paragraph.text
                                })
                    if sub_info["paragraphs"]:
                        sub_shapes_data.append(sub_info)
                if sub_shapes_data:
                    shape_info["group_sub_shapes"] = sub_shapes_data

            if shape_info["paragraphs"] or shape_info.get("group_sub_shapes"):
                slide_data["shapes"].append(shape_info)
                
        # 处理备注
        if slide.has_notes_slide and slide.notes_slide.notes_text_frame:
            notes_info = {
                "shape_index": "notes",
                "paragraphs": []
            }
            for p_idx, paragraph in enumerate(slide.notes_slide.notes_text_frame.paragraphs):
                if paragraph.text.strip():
                    notes_info["paragraphs"].append({
                        "type": "notes",
                        "p_index": p_idx,
                        "text": paragraph.text
                    })
            if notes_info["paragraphs"]:
                slide_data["shapes"].append(notes_info)
                
        data.append(slide_data)
        
    return data

def main():
    path = r"C:\Users\Dell\Downloads\image_compression_presentation_editable_cleaned_v2.pptx"
    out_json = r"C:\Users\Dell\Downloads\ppt_extracted_text.json"
    
    if not os.path.exists(path):
        print(f"Error: File not found at {path}")
        return
        
    data = extract_text_from_ppt(path)
    with open(out_json, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        
    print(f"Successfully extracted PPT text to {out_json}")

if __name__ == '__main__':
    main()
