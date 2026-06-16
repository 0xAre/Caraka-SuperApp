import re

def scale_path(path_str):
    # Tokens are M, c, z, and numbers (which may be negative)
    tokens = re.findall(r'[Mcz]|[-+]?\d*\.?\d+', path_str)
    out = []
    
    current_cmd = None
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if t in ('M', 'c', 'z'):
            current_cmd = t
            out.append(t)
            i += 1
            continue
            
        if current_cmd == 'M':
            # M takes 2 coordinates: X, Y
            x = float(tokens[i])
            y = float(tokens[i+1])
            new_x = x * 0.1
            new_y = y * -0.1 + 1000.0
            out.append(f"{new_x:.1f}")
            out.append(f"{new_y:.1f}")
            i += 2
        elif current_cmd == 'c':
            # c takes coordinates in multiples of 2. All are relative, so no +1000
            x = float(tokens[i])
            y = float(tokens[i+1])
            new_x = x * 0.1
            new_y = y * -0.1
            out.append(f"{new_x:.1f}")
            out.append(f"{new_y:.1f}")
            i += 2
            
    return " ".join(out)

# test
p = "M4805 8804 c-16 -2 -84 -9"
print(scale_path(p))
