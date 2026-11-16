"""Build the Wildgrass wardrobe reference outfits in Blender and export their bind meshes.

Run with blender --background --python art/build_survivors.py.
The game's armature uses meters, Y up and -Z forward.
"""

from pathlib import Path
import math
import struct

import bmesh
import bpy
from mathutils import Vector


ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / "src/main/resources/models"
SIDES = 12
BONES = [
    ("pelvis", None, (0, .83, 0), (0, 1, 0)),
    ("head", 12, (0, 1.47, 0), (0, 1.83, 0)),
    ("upper_arm.L", 12, (-.267, 1.34, 0), (-.355, 1.09, 0)),
    ("forearm.L", 2, (-.355, 1.09, 0), (-.405, .822, -.062)),
    ("upper_arm.R", 12, (.267, 1.34, 0), (.355, 1.09, 0)),
    ("forearm.R", 4, (.355, 1.09, 0), (.405, .822, -.062)),
    ("thigh.L", 0, (-.14, .83, 0), (-.153, .47, 0)),
    ("shin.L", 6, (-.153, .47, 0), (-.153, .107, 0)),
    ("thigh.R", 0, (.14, .83, 0), (.153, .47, 0)),
    ("shin.R", 8, (.153, .47, 0), (.153, .107, 0)),
    ("boot.L", 7, (-.153, .107, 0), (-.153, .07, -.16)),
    ("boot.R", 9, (.153, .107, 0), (.153, .07, -.16)),
    ("spine", 0, (0, .83, 0), (0, 1.47, 0)),
]


def xyz(p):
    return (p[0], -p[2], p[1])


def game_xyz(p):
    return (p.x, p.z, -p.y)


def material(label, hex_color):
    color = tuple(int(hex_color[i:i+2], 16) / 255 for i in (0, 2, 4))
    mat = bpy.data.materials.new(label)
    mat.diffuse_color = (*color, 1)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (*color, 1)
    bsdf.inputs["Roughness"].default_value = .91
    return mat


def smoothstep(start, end, x):
    t = max(0, min(1, (x - start) / (end - start)))
    return t*t*(3-2*t)


def blend(low, high, start, end):
    def weights(p):
        t = smoothstep(start, end, p[1])
        return {low: 1-t, high: t}
    return weights


def rig(collection, label):
    armature = bpy.data.armatures.new(label + " skeleton")
    obj = bpy.data.objects.new(label + " skeleton", armature)
    collection.objects.link(obj)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    obj.show_in_front = True
    bpy.ops.object.mode_set(mode='EDIT')
    for name, _, head, tail in BONES:
        bone = armature.edit_bones.new(name)
        bone.head, bone.tail = xyz(head), xyz(tail)
    for name, parent, _, _ in BONES:
        if parent is not None:
            armature.edit_bones[name].parent = armature.edit_bones[BONES[parent][0]]
    bpy.ops.object.mode_set(mode='OBJECT')
    obj.select_set(False)
    return obj


class Outfit:
    def __init__(self, label, colors):
        self.collection = bpy.data.collections.new(label)
        bpy.context.scene.collection.children.link(self.collection)
        self.rig = rig(self.collection, label)
        self.mat = {key: material(label + " · " + key, color) for key, color in colors.items()}
        self.export = []

    def loft(self, label, rings, color, weights, *, sides=SIDES, smooth=True, preview=False):
        """Each ring is (height, center x, center z, X radius, Z radius)."""
        points = [(x + rx*math.cos(math.tau*i/sides), y, z + rz*math.sin(math.tau*i/sides))
                  for y, x, z, rx, rz in rings for i in range(sides)]
        faces = [tuple(reversed(range(sides)))]
        for row in range(len(rings)-1):
            for i in range(sides):
                a = row*sides+i
                b = row*sides+(i+1)%sides
                faces.append((a, b, b+sides, a+sides))
        faces.append(tuple(range(len(points)-sides, len(points))))
        return self.mesh(label, points, faces, color, weights, smooth, preview)

    def mesh(self, label, points, faces, color, weights, smooth=False, preview=False):
        mesh = bpy.data.meshes.new(label)
        mesh.from_pydata([xyz(p) for p in points], [], faces)
        mesh.update()
        bm = bmesh.new()
        bm.from_mesh(mesh)
        bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
        bm.to_mesh(mesh)
        bm.free()
        obj = bpy.data.objects.new(label, mesh)
        self.collection.objects.link(obj)
        mesh.materials.append(self.mat[color])
        for face in mesh.polygons:
            face.use_smooth = smooth and len(face.vertices) == 4
        groups = {}
        for i, point in enumerate(points):
            influence = {weights: 1} if isinstance(weights, int) else weights(point)
            for bone, value in influence.items():
                if value > 0:
                    if bone not in groups:
                        groups[bone] = obj.vertex_groups.new(name=BONES[bone][0])
                    groups[bone].add([i], value, 'REPLACE')
        binding = obj.modifiers.new("Wildgrass skeleton", 'ARMATURE')
        binding.object = self.rig
        obj.parent = self.rig
        if not preview:
            self.export.append(obj)
        return obj

    def box(self, label, center, half, color, weights, bevel=.022):
        x, y, z = center
        a, b, c = half
        points = [(x + sx*a, y + sy*b, z + sz*c)
                  for sy in (-1, 1) for sx,sz in [(-1,-1),(1,-1),(1,1),(-1,1)]]
        obj = self.mesh(label, points, [(3,2,1,0),(4,5,6,7),(0,1,5,4),
                         (1,2,6,5),(2,3,7,6),(3,0,4,7)], color, weights)
        # Bevel the edit mesh so exported vertices can keep their armature weights.
        mod = obj.modifiers.new("Soft corners", 'BEVEL')
        mod.width, mod.segments = bevel, 2
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.modifier_move_up(modifier=mod.name)
        bpy.ops.object.modifier_apply(modifier=mod.name)
        return obj

    def ellipsoid(self, label, center, radii, color, weights, *, sides=SIDES, preview=False):
        x, y, z = center
        a, b, c = radii
        return self.loft(label, [
            (y+b*t, x, z, a*r, c*r) for t,r in
            [(-1,.08),(-.81,.59),(-.42,.91),(0,1),(.42,.91),(.81,.59),(1,.08)]
        ], color, weights, sides=sides, preview=preview)

    def write(self, path):
        out = bytearray()
        count = 0
        for obj in self.export:
            mesh = obj.data
            mesh.calc_loop_triangles()
            color = mesh.materials[0].diffuse_color
            for triangle in mesh.loop_triangles:
                for loop, index in zip(triangle.loops, triangle.vertices):
                    v = mesh.vertices[index]
                    influences = [(obj.vertex_groups[g.group].name, g.weight) for g in v.groups if g.weight > 0]
                    if not influences:
                        raise RuntimeError(f"Unbound vertex {obj.name}[{index}]")
                    influences = sorted(influences, key=lambda i: i[1], reverse=True)[:4]
                    total = sum(weight for _,weight in influences)
                    ids = [[b[0] for b in BONES].index(name) for name,_ in influences]
                    weights = [weight/total for _,weight in influences]
                    ids += [0]*(4-len(ids))
                    weights += [0]*(4-len(weights))
                    out.extend(struct.pack(">18f", *game_xyz(v.co), *game_xyz(mesh.corner_normals[loop].vector),
                                           *color[:3], 0, *ids, *weights))
                    count += 1
        path.write_bytes(struct.pack(">II", 0x57415631, count) + out)
        print(f"{path.name}: {count//3} triangles")


def build(outfit, camp):
    palette = (dict(skin="B87954", hair="493127", jacket="9E6047", trim="AB7559", tee="50483A",
                    pants="615E50", boot="44382D", sole="2E2823", hat="565246", bag="6A5140",
                    scarf="C09A52", strap="58412D", brass="C3A36B")
               if camp else
               dict(skin="A9A166", hair="54462D", jacket="7E8053", trim="919061", tee="D5C8A5",
                    pants="746045", boot="4D4030", sole="312821", hat="848455", bag="B29A68",
                    scarf="BBB07F", strap="765839", brass="CEC292"))
    player = Outfit(outfit, {**palette, "eye": "FFF0CD", "pupil": "312521"})
    p = player
    p.loft("Head · face", [
        (1.44,0,.027,.10,.11),(1.49,0,.005,.166,.16),(1.55,0,-.005,.211,.202),
        (1.63,0,-.01,.236,.218),(1.73,0,.005,.228,.215),(1.80,0,.023,.206,.197),
        (1.865,0,.026,.148,.143),(1.90,0,.028,.065,.070)], "skin", 1, sides=16)
    p.ellipsoid("Head · nose", (0,1.596,-.211), (.036,.036,.034), "skin", 1)
    p.ellipsoid("Neck", (0,1.45,.033), (.103,.137,.102), "skin", blend(12,1,1.38,1.52))
    for side in (-1,1):
        p.ellipsoid("Ear", (side*.224,1.624,.014), (.047,.066,.042), "skin", 1)
        p.ellipsoid("Sideburn", (side*.207,1.717,.078), (.037,.090,.075), "hair", 1)
        p.ellipsoid("Eye · preview", (side*.097,1.645,-.218), (.047,.039,.023), "eye", 1, preview=True)
        p.ellipsoid("Pupil · preview", (side*.097 + .006,1.645,-.240), (.018,.019,.008), "pupil", 1, preview=True)

    if camp:
        p.loft("Headwear · knit cap", [
            (1.742,0,.022,.210,.199),(1.767,0,.022,.235,.218),(1.83,0,.027,.231,.214),
            (1.91,0,.034,.200,.184),(1.956,0,.041,.144,.128),(1.981,0,.043,.062,.059)], "hat", 1, sides=14)
        p.loft("Headwear · knit cuff", [(1.748,0,.023,.217,.201),(1.767,0,.022,.243,.226),
               (1.804,0,.025,.240,.222),(1.815,0,.026,.228,.215)], "boot", 1, sides=14)
        for angle in (-.81,-.54,-.27,0,.27,.54,.81):
            ridge = []
            for y, rx, rz in [(1.768,.245,.228),(1.803,.243,.225),(1.833,.232,.218),(1.892,.211,.199)]:
                for a in (angle-.025,angle+.025):
                    ridge.append((rx*math.sin(a),y,.027-rz*math.cos(a)))
            p.mesh("Headwear · knit rib", ridge, [(i,i+1,i+3,i+2) for i in (0,2,4)], "hat", 1)
        p.loft("Neckwear · scarf", [(1.367,0,.019,.134,.137),(1.40,0,.009,.150,.151),
               (1.453,0,.009,.140,.137),(1.477,0,.013,.118,.122)], "scarf", blend(12,1,1.40,1.54))
        p.box("Scarf · knot", (.042,1.405,-.145), (.049,.037,.030), "scarf", 12)
        p.box("Scarf · tail", (.068,1.327,-.153), (.028,.075,.017), "scarf", 12)
    else:
        p.loft("Headwear · field cap", [(1.745,0,.028,.214,.199),(1.771,0,.03,.245,.218),
               (1.853,0,.035,.224,.201),(1.918,0,.040,.179,.164),(1.959,0,.044,.096,.094)],
               "hat", 1, sides=14)
        p.loft("Headwear · visor", [(1.743,0,-.194,.179,.138),(1.753,0,-.213,.223,.151),
               (1.770,0,-.209,.218,.143),(1.779,0,-.180,.176,.112)], "trim", 1, sides=16)

    p.loft("Torso · canvas jacket", [
        (.841,0,.021,.207,.130),(.875,0,.021,.229,.146),(1.05,0,.023,.218,.129),
        (1.26,0,.031,.239,.135),(1.36,0,.034,.249,.127),(1.423,0,.038,.178,.109),
        (1.439,0,.027,.111,.100)], "jacket", blend(0,12,.82,1.08))
    # Follow the convex front of the jacket so the undershirt reads as cloth.
    panel = []
    for y, width, z in [(1.420,.050,-.078),(1.350,.075,-.099),(1.215,.063,-.109),
                        (1.055,.048,-.113),(.867,.044,-.128)]:
        panel.extend([(-width,y,z+.004),(0,y,z-.004),(width,y,z+.004)])
    p.mesh("Torso · undershirt", panel, [(i+j,i+j+1,i+j+4,i+j+3)
           for i in range(0,12,3) for j in range(2)], "tee", blend(0,12,.82,1.08))
    for side in (-1,1):
        p.mesh("Jacket · lapel", [(side*.053,1.413,-.083),(side*.120,1.369,-.096),
               (side*.098,1.262,-.115),(side*.065,1.309,-.111)], [(0,1,2,3)], "trim", 12)
    for side, upper, fore, thigh, shin, foot in [(-1,2,3,6,7,10),(1,4,5,8,9,11)]:
        w = blend(fore, upper, 1.015, 1.18)
        p.loft("Jacket · sleeve", [(.875,side*.389,-.015,.081,.080),(.919,side*.375,-.003,.086,.088),
            (1.059,side*.346,.005,.086,.087),(1.166,side*.316,.019,.102,.096),
            (1.286,side*.273,.024,.110,.106),(1.344,side*.252,.027,.085,.093)], "jacket", w)
        p.loft("Jacket · cuff", [(.864,side*.389,-.018,.083,.083),(.876,side*.389,-.017,.092,.089),
            (.914,side*.378,-.005,.092,.091),(.924,side*.376,-.004,.080,.083)], "trim", fore)
        p.ellipsoid("Hand · palm", (side*.399,.831,-.026), (.073,.107,.076), "skin", fore)
        p.ellipsoid("Hand · thumb", (side*.354,.827,-.080), (.033,.057,.044), "skin", fore)
        p.box("Jacket · lower pocket", (side*.125,1.013,-.109), (.051,.061,.018),
              "trim", blend(0,12,.82,1.08), .012)
        p.box("Jacket · pocket flap", (side*.126,1.071,-.121), (.057,.018,.017),
              "jacket", blend(0,12,.82,1.08), .009)
        p.box("Pack · shoulder strap", (side*.179,1.242,-.091), (.020,.160,.024), "strap", 12, .012)
        p.box("Pack · strap buckle", (side*.177,1.147,-.116), (.025,.024,.010), "brass", 12, .007)
        p.loft("Legwear · field trousers", [(.125,side*.153,.009,.074,.077),
            (.193,side*.153,.008,.081,.087),(.391,side*.153,.015,.079,.084),
            (.49,side*.146,.010,.086,.095),(.65,side*.13,.013,.096,.105),
            (.788,side*.114,.023,.108,.121),(.902,side*.107,.022,.103,.122)],
            "pants", blend(shin,thigh,.40,.56))
        p.box("Legwear · cargo pocket", (side*.214,.62,-.038), (.029,.080,.052), "pants", thigh, .012)
        p.loft("Footwear · boot", [(.039,side*.153,-.052,.093,.153),(.071,side*.153,-.050,.101,.154),
            (.113,side*.153,-.044,.096,.145),(.145,side*.153,-.012,.078,.097),
            (.209,side*.153,.012,.080,.083)], "boot", foot)
        p.loft("Footwear · sole", [(.003,side*.153,-.054,.091,.148),(.015,side*.153,-.055,.104,.161),
            (.046,side*.153,-.054,.101,.157),(.053,side*.153,-.051,.092,.151)], "sole", foot)
    p.box("Back · canvas pack", (0,1.14,.204), (.166,.213,.105), "bag", 12, .052)
    p.box("Back · pack flap", (0,1.287,.269), (.158,.071,.060), "trim", 12, .035)
    if camp:
        roll = p.ellipsoid("Back · bedroll", (0,0,0), (.090,.280,.088), "scarf", 12)
        for v in roll.data.vertices:
            x,y,z = game_xyz(v.co)
            v.co = xyz((y,1.418-x,.250+z))
        roll.data.update()
    return player


bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
explorer = build("A · Field explorer", False)
camper = build("B · Camp survivor", True)
explorer.write(MODELS / "survivor-explorer.wavatar")
camper.write(MODELS / "survivor-camper.wavatar")
explorer.rig.location.x = -.48
camper.rig.location.x = .48
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type == "VIEW_3D":
            space = area.spaces.active
            space.shading.color_type = 'MATERIAL'
            space.overlay.show_overlays = False
            view = space.region_3d
            view.view_location = Vector((0,0,1))
            view.view_rotation = Vector((.13,1,.14)).to_track_quat('Z','Y')
            view.view_distance = 3.5
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / "art/wildgrass-survivors.blend"), compress=True)
