import io
import tempfile
import unittest
from pathlib import Path
import nbtlib as nbt
import numpy as np
from prepare_world import indices, set_indices, write_region, region_chunks, process, strip_selected, introductions

class PreparationTest(unittest.TestCase):
    def test_palette_packing_roundtrip_at_width_boundaries(self):
        for size in (2, 16, 17, 32, 33, 65, 257):
            states=nbt.Compound({'palette':nbt.List[nbt.Compound]([
                nbt.Compound({'Name':nbt.String('minecraft:stone')}) for _ in range(size)])})
            values=np.arange(4096,dtype=np.uint64)%size
            set_indices(states,values)
            np.testing.assert_array_equal(values,indices(states))

    def test_preparation_is_deterministic_finite_and_refuses_overwrite_or_missing_chunks(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); source=root/'source'; region=source/'dimensions/minecraft/overworld/region';region.mkdir(parents=True)
            doc=nbt.File({'xPos':nbt.Int(0),'zPos':nbt.Int(0),'Status':nbt.String('minecraft:full'),
                'InhabitedTime':nbt.Long(0),'sections':nbt.List[nbt.Compound]([nbt.Compound({
                    'Y':nbt.Byte(0),'block_states':nbt.Compound({'palette':nbt.List[nbt.Compound]([
                        nbt.Compound({'Name':nbt.String('minecraft:diamond_ore')})])})})])})
            write_region(region/'r.0.0.mca',[(0,doc)])
            spec={'format':1,'seed':42,'bounds':[0,-64,0,15,319,15],
                'deposits':[{'id':'test','bounds':[0,0,0,15,15,15],'ore-count':23}]}
            first=process(source,spec,root/'first');second=process(source,spec,root/'second')
            self.assertEqual(4096,first['removed-original-ore'])
            self.assertEqual(first['prepared'],second['prepared'])
            audited=process(root/'first',spec)
            self.assertEqual(23,audited['observed']['ore-blocks'])
            self.assertEqual(0,audited['observed']['ore-outside-deposits'])
            self.assertEqual({'test':23},audited['deposits'])
            self.assertEqual(4096,process(source,spec)['observed']['ore-blocks'])
            with self.assertRaisesRegex(ValueError,'must not exist'): process(source,spec,root/'first')
            marker=source/'PREPARATION-INCOMPLETE';marker.write_text('test')
            with self.assertRaisesRegex(ValueError,'incomplete'):process(source,spec)
            marker.unlink()
            spec['bounds'][3]=31
            with self.assertRaisesRegex(ValueError,'missing'):process(source,spec)

    def test_selected_item_cleanup_preserves_unrelated_inventory_and_handles_nested_items(self):
        items=nbt.List[nbt.Compound]([
            nbt.Compound({'id':nbt.String('minecraft:diamond'),'count':nbt.Int(2)}),
            nbt.Compound({'id':nbt.String('minecraft:emerald'),'count':nbt.Int(3)}),
            nbt.Compound({'id':nbt.String('minecraft:bundle'),'components':nbt.Compound({'contents':nbt.List[nbt.Compound]([
                nbt.Compound({'id':nbt.String('minecraft:deepslate_diamond_ore')})])})}),
        ])
        strip_selected(items)
        self.assertEqual(['minecraft:emerald','minecraft:bundle'],[str(i['id']) for i in items])
        self.assertEqual(3,int(items[0]['count']))
        self.assertEqual(0,len(items[1]['components']['contents']))

    def test_lazy_loot_prevents_preparation(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);region=root/'source/dimensions/minecraft/overworld/region';region.mkdir(parents=True)
            doc=nbt.File({'xPos':nbt.Int(0),'zPos':nbt.Int(0),'Status':nbt.String('minecraft:full'),
                'block_entities':nbt.List[nbt.Compound]([nbt.Compound({'LootTable':nbt.String('minecraft:chests/test')})]),
                'sections':nbt.List[nbt.Compound]([])})
            write_region(region/'r.0.0.mca',[(0,doc)])
            spec={'format':1,'seed':42,'bounds':[0,-64,0,15,319,15],
                'deposits':[{'id':'test','bounds':[0,0,0,15,15,15],'ore-count':1}]}
            with self.assertRaisesRegex(ValueError,'alternate supply'):process(root/'source',spec,root/'out')
            self.assertFalse((root/'out').exists())

if __name__=='__main__':unittest.main()
